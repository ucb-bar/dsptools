DSP Streaming Interface
=======================

# Overview

The goal is to have a consistent streaming interface between DSP blocks to facilitate
* arbitrary composition of DSP blocks.
* automatic Design For Test (DFT) insertion, using the Built-In Logic Analyzer (BILA) and Built-In Pattern Generator (BIPG).
* convenient verification and testing using existing tools and verification flows.

We are using a subset of the AXI4-Stream interface to accomplish these goals.

# Interface

## Parameters

* gen: datatype of the bits field, must be of underlying type Data

## Signals

The interface consists of 5 signals. All signals are one way, meaning downstream blocks cannot apply back pressure.

* __clock__ - the clock, an implicit in Chisel
* __reset__ - active high reset, an implicit in Chisel (Note: this is active high because that’s the default in Chisel; the AXI4-Stream spec has it active low)
* __bits__ - called TDATA in AXI4-Stream, this field of type gen contains the actual data to send over the stream (packing must be done to be like AXI4-Stream)
* __valid__ - called TVALID in AXI4-Stream, this 1-bit signal says whether the bits on this cycle are valid (high) or not (low)
* __sync__ - called TLAST in AXI4-Stream, this 1-bit signal marks the end of a packet when high, typically the end of a spectrum; the next cycle, when valid is high, marks the beginning of a new spectrum


# Usage
 
## Signaling

### Bits

The bits field can always be set to whatever the user wants. But it should contain valid data on every cycle that valid is high.

### Valid

Valid at the input or output indicates that the bits field contains valid data this cycle. When valid is low, the bits field is invalid, and should be ignored. A high valid also indicates that sync is valid this cycle, whether 0 or 1. If valid is low, a high sync should be ignored.

### Sync

The expected usage of sync is to mark packet or spectral boundaries. To maintain consistence with AXI4-Stream, sync should be high at the last cycle of a packet or spectrum. Valid must be high for the sync signal to be valid; when valid is low, sync should be ignored. It is recommended to keep sync low when valid is low.

## Chisel

To use this interface in Chisel, make sure your design depends on rocket-dsp-utils. Add the following import to your code.

```
import dspjunctions._
```

Then, in your IO bundle, instantiate a ValidWithSync input or output.

```
val io = IO(new Bundle {
  val in = Input(ValidWithSync(genIn))
  val out = Output(ValidWithSync(genOut))
}
```

To reference the fields, use the following examples.

```
val sync = io.in.sync
val data = io.in.bits
io.out.valid := io.in.valid
```

