# AXI4-Stream

AXI4-Stream is an AMBA standard for streaming interfaces.
The standard has many optional fields, but at its core is fairly simple.
It defines the semantics of a ready/valid handshake, and then there are a number of fields that contain, describe, or modify data.

The `AXI4Bundle` type contains the following fields:

* `valid`
* `ready`
* `bits`:
    - `data`
    - `strb`
    - `keep`
    - `last`
    - `id`
    - `dest`
    - `user`

In the standard, all signals except `valid` are optional.
In the implementation here, `ready` and `last` are also mandatory for Chisel-implementation reasons.
It is desireable that the bundle be a `DecoupledIO` to make it easy to use with library components like `Queue`, `Arbiter`, etc.
This means that it is difficult to remove `ready`.

The mechanism by which unwanted signals are omitted is by setting their widths to zero.
This means that the optional signals will still be visible to scala programs, but will be removed in the generated code.
Zero-width `UInt`s are treated as zero by the FIRRTL compiler, so care should be taken that you aren't accidentally relying on a removed signal.
In many cases, an absent signal being replaced with 0 is fine, but special care should be taken for `strb` and `keep`.

## Basic Usage
The AXI4-Stream bundles can be used directly, but the recommended way to use AXI4-Stream is as a [diplomatic interface](Diplomacy.md).
This means there is a two-stage elaboration: in the first, parameters are elaborated by a `Node`, and in the second hardware is generated, giving you a `Bundle` to work with.
For example:

```
class LM extends LazyModule {
  // used in stage 1 of elaboration
  // identity node enforces in and out have same parameters
  val streamNode = AXI4StreamIdentityNode()
  
  lazy val module = new LazyModuleImp(this) {
    // used in stage 2 of elaboration
    // in and out are bundles corresponding to the input and output streaming bundles
    // (the underscores are parameters objects- sometimes its nice to see the
    // parameters that diplomacy came up with
    val (ins, _)  = streamNode.in.unzip
    val (outs, _) = streamNode.out.unzip
    ins.zip(outs).foreach { case (in, out) =>
      out <> in // connect in to out
    }
  }
}
```

## Node Types

- `AXI4StreamIdentityNode`
- `AXI4StreamMasterNode`
- `AXI4StreamSlaveNode`
- `AXI4StreamNexusNode`
- `AXI4StreamAdapterNode`

There are also async node types that can be used for automatic clock crossings.

## Useful Blocks
- `AXI4StreamFuzzer`: a fuzzer useful in synthesizable tests
- `AXI4StreamWidthAdapter`: adapters for adjusting the width by integer ratios
- `Mux`: programmable number of inputs and outputs, with CSRs to set which input goes to which output
- `DMA`: a stream <-> memory map (AXI-4) DMA.

### Async Crossings

Async node types are defined for AXI4-Stream.
This allows you to use crossing wrappers to cross in or out of a clock domain.
However, the built-in rocket `CrossingWrapper` doesn't know about this implementation of AXI4-Stream, so the trait `HasAXI4StreamCrossing` needs to be mixed in.

Here's a sketch of what clock crossings with AXI4-Stream might look like.

```
  val island = LazyModule(new CrossingWrapper(AsynchronousCrossing()) with HasAXI4StreamCrossing)
  island.clock := someOtherClock
  val streamBlock = island { LazyModule(new WhateverStreamModule) }
  val out = AXI4StreamSlaveNode()
  out := island.crossAXI4StreamOut(streamBlock.streamNode)
```

## Model
`AXI4StreamModel` is a mixin trait that can be added to a Chisel `PeekPokeTester`.
It adds the ability to add VIP-style drivers and slave/monitors to AXI4-Stream interfaces.
This lets you specify behavior at a transaction level and not worry about the cycle-by-cycle behavior.
`AXI4StreamModel` is composed of two more granular traits: `AXI4StreamMasterModel` and `AXI4StreamSlaveModel` which only provide drivers and slaves respectively.

A driver is made by calling `bindMaster(port)` on a top-level AXI4-Stream port, and a slave is made by calling `bindSlave(port)`.
After making the master and slave drivers, the test code should enqueue transactions and expected transactions.
`step()` will still step the clock, and the drivers will manage the streaming interfaces for you.
`stepUntilCompletion()` will step until every driver is done, or until a programmable timeout.

The `PassthroughTester` in dsptools is a good example:

```
  val master = bindMaster(in)
  val slave = bindSlave(out)

  // fill queue
  master.addTransactions((0 until expectedDepth).map(x => AXI4StreamTransaction(data = x)))
  stepToCompletion()

  // queue should be full
  expect(in.ready, 0)
  expect(out.valid, 1)

  // empty queue
  slave.addExpects((0 until expectedDepth).map(x => AXI4StreamTransactionExpect(data = Some(x))))
  stepToCompletion()
```
