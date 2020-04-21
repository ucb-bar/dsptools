# DspBlock

> It may be helpful to start with some background information about [diplomacy](Diplomacy.md)

A DspBlock is a unit of signal processing hardware that can be integrated into an SoC.
It has a streaming interface and a memory interface.
More concretely, `DspBlock` is a trait that has two members:
* an AXI4-Stream diplomatic node
* an optional, abstract memory interface diplomatic node

The memory node is optional, i.e. it is of type `Option[T]`, where `T` is the type of the node.
If the memory node is `None`, the block will not have a memory interface and will only have streaming interfaces.

The memory node is also abstract.
In practice this means that a `DspBlock` can utilize any of the memory interfaces supported by rocket (AHB, APB, AXI-4, TileLink).
It is a common design pattern to make abstract versions of a `DspBlock` and then bind impelmentations of the memory interface later, for example:
* An abstract `FIRBlock` that describes an FIR filter with programmable taps
* `AXI4FIRBlock extends FIRBlock` that instantiates an AXI-4 interface
* `TLFIRBlock extends FIRBlock` that instantiates a TileLink interface
In fact, a library author could make their own diplomatic implementation of a memory interface not supported by rocket (along with an implementation of regmapper) and make their own flavor of `FIRBlock` without changing the original `FIRBlock` class at all.

The definition of `DspBlock` is
```
trait DspBlock[D, U, EO, EI, B <: Data] extends LazyModule { ... }
```

The type parameters `D`, `U`, `EO`, and `EI` are parameter types of the memory interface and `B` is the bundle type of the memory interface.
There are flavors of `DspBlock` for each memory interface, for example
```
trait TLDspBlock extends DspBlock[
  TLClientPortParameters,
  TLManagerPortParameters,
  TLEdgeOut,
  TLEdgeIn,
  TLBundle] { ... }
```

## CSRs
Rocketchip's `regmap` API is a useful way of generating logic for control and status registers (CSRs).
The `regmap` API is generic with respect to the type of the memory interface.
The trait `HasCSR` can be mixed into `DspBlock` to make the block's memory node use the regmap API.
An example `DspBlock` called `ByteRotate` is [here](https://github.com/ucb-bar/dsptools/blob/bd5b0912ef0c85226d6d53cf6a07ce43e2a0d959/rocket/src/main/scala/dspblocks/BasicBlocks.scala#L95)- note the call to `regmap()`.

```
// D, U, EO, EI are memory interface parameter types
// B is memory interface bundle type
abstract class ByteRotate[D, U, EO, EI, B <: Data]()(implicit p: Parameters) extends DspBlock[D, U, EO, EI, B] with HasCSR {
  // Identity node- input and output are the same
  val streamNode = AXI4StreamIdentityNode()

  // module is the hardware that gets generated after parameters are resolved
  lazy val module = new LazyModuleImp(this) {
    // get bundles for streaming inputs and outputs
    val (in, _)  = streamNode.in.unzip
    val (out, _) = streamNode.out.unzip
    val n = in.head.bits.params.n
    val nWidth = log2Ceil(n) + 1

    // register to store rotation amount
    val byteRotate = RegInit(0.U(nWidth.W))

    def rotateBytes(u: UInt, n: Int, rot: Int): UInt = {
      Cat(u(8*rot-1, 0), u(8*n-1, 8*rot))
    }

    out.head.valid := in.head.valid
    in.head.ready  := out.head.ready
    out.head.bits  := in.head.bits

    for (i <- 1 until n) {
      when (byteRotate === i.U) {
        out.head.bits.data := rotateBytes(in.head.bits.data, n, i)
      }
    }

    // generate logic for memory interface
    regmap(
      // address 0 -> byteRotate register
      // uses default read and write behavior- can override with RegReadFn and RegWriteFn
      0x0 -> Seq(RegField(1 << log2Ceil(nWidth), byteRotate))
    )
  }
}

```

`regmap()` takes a list of pairs (address -> Seq(field)).
The `Seq()` of fields is a list of `RegFields()`, see [rocketchip](https://github.com/chipsalliance/rocket-chip/blob/master/src/main/scala/regmapper/RegField.scala) for a list of all the fields.
There are sensible defaults for reading and writing a register, as well as the ability to add custom behavior on reads and writes.

## Chains
Composability is important for libraries to be useful.
A group of `DspBlock`s can be connected to form a large `DspBlock`.

`HierarchicalBlock` is the general version of this concept.
It has a list of blocks (called `blocks: Seq[Block]`) and a list of connections (called `connections: Seq[(Block, Block)]`).
It also defines a `connect` function that describes how the edges in `connections` should be connected.
The default is to simply do the diplomatic connection on the `streamNode` (`lhs.streamNode := rhs.streamNode`), but it may be desireable to add queues, instrumentation, etc.

One version of a `HierarchicalBlock` is a `Chain`.
A `Chain` connects blocks sequentially, makes a crossbar, and connects every block with a memory interface to the crossbar.
Because `Chain`s are themselves `DspBlock`s, `Chain`s can be nested.

## Standalone Blocks
The point of diplomacy is to enable parameters to be negotiated by different blocks across a design.
Diplomacy can make unit testing somewhat difficult:
- Diplomatic nodes are not meant to be top level IOs, but unit tests need the DUT to be top level
- Sink and/or source nodes are needed to parameterize diplomatic nodes

Preparing a `DspBlock` to be unit tested (especially by chisel-testers `PeekPokeTester`) can be tedious and error-prone, so dsptools includes functionality to automate making `DspBlock` a top-level standalone DUT.
This is achieved with mixin traits:
- `StandaloneBlock` is the base trait and creates top level IOs that are connected to AXI4StreamMasterNode and AXI4StreamSlaveNode for the input and output of the `DspBlock`
- Flavors like `TLStandaloneBlock` and `AXI4StandaloneBlock` specialize to specific memory interfaces

`StandaloneBlock` et. al. should not generally be mixed in with your `DspBlock`'s class.

```
// DON'T DO THIS!!! (UNLESS YOU'RE POSITIVE IT'S WHAT YOU WANT)
class MyBlock() extends AXI4DspBlock with AXI4StandaloneBlock { ... }
```

Instead, you should mixin in your tester, like these truncated examples from [here](https://github.com/ucb-bar/dsptools/blob/master/rocket/src/test/scala/dspblocks/BasicBlockTesters.scala):

```
abstract class PassthroughTester[D, U, EO, EI, B <: Data](dut: Passthrough[D, U, EO, EI, B] with StandaloneBlock[D, U, EO, EI, B])
extends PeekPokeTester(dut.module)
class AXI4PassthroughTester(c: AXI4Passthrough with AXI4StandaloneBlock)
  extends PassthroughTester(c)
```

The tester then needs to be invoked with the mixin, [like so](https://github.com/ucb-bar/dsptools/blob/master/rocket/src/test/scala/dspblocks/DspBlockSpec.scala#L15):
```
// do the mixin here
val lazymod = LazyModule(new AXI4Passthrough(params) with AXI4StandaloneBlock)
val dut = () => lazymod.module

chisel3.iotesters.Driver.execute(Array("-tbn", "firrtl", "-fiwv"), dut) {
  c => new AXI4PassthroughTester(lazymod)
} should be (true)
```

`StandaloneBlock` et. al. work by using Rocketchip's `BundleBridge`s.
`BundleBridge`s are diplomatic nodes that can contain any kind of bundle and can also be used to punch out IOs.
Diplomatic converters converter `BundleBridge`s to/from AXI4-Stream, AXI-4, TileLink, etc., and the `BundleBridge` is punched out to IOs.
