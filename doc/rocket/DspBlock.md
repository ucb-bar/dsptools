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
regmap(
  0x0 -> Seq(RegField(1 << log2Ceil(nWidth), byteRotate))
) 
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
A `Chain` connects blocks sequentially.
