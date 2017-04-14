JTAG to AXI4 Interface
======================

# Overview

DspChains can optionally have another AXI master into both the data and control crossbars that are controllable via JTAG.

# Parameters

The Jtag->AXI4 master is added by the rocket parameter `DspChainIncludeJtag`.

# Implementation

The instruction register is 4 bits long.
Each AXI channel is connected to either a source or sink JTAG chain.
The following table shows what specific values of the IR will select.

| IR   | Description       |
| ---- | ----------------- |
| 0    | Control AW source |
| 1    | Control W source  |
| 2    | Control B sink    |
| 3    | Control AR source |
| 4    | Control R sink    |
| 5    | Data AW source    |
| 6    | Data W source     |
| 7    | Data B sink       |
| 8    | Data AR source    |
| 9    | Data R sink       |
| 14   | ID code           |
| 15   | BYPASS            |

Each AXI channel sink or source has a decoupled interface with ready and valid interfaces.
Each source and sync chain should be connected to an async fifo so ready and valid will be held correctly.

The behavior of a source channel is that the ready signal is latched during the JTAG capture state.
If ready was true, the valid will be true during the JTAG update state; otherwise, valid will be false.
It is assumed that if ready was true during the capture state, it will stay ready during the update state.
In general this is required by the AXI4 spec, but with an async fifo it will be true.

The behavior of a sink channel is that the valid signal is latched with the data during the JTAG capture state.
Ready will always be true during the capture state.

It is not necessary to reset the IR between multiple of a channel.

# Usage

Inputs for source chains consist of the fields of the AXI channel.

* AW consists of (in order)
    * addr
    * len
    * size
    * burst
    * lock
    * cache
    * prot
    * qos
    * region
    * id
    * user
* W consists of (in order)
    * data
    * last
    * id
    * strb
    * user
* AR consists of (in order)
    * addr
    * len
    * size
    * burst
    * lock
    * cache
    * prot
    * qos
    * region
    * id
    * user

The scan output for source chains consists only of the ready bit of the AXI channel.

Inputs for sink chains are ignored.
The output for sink chains consist of a valid bit and the fields of the AXI channel.

* B consists of (in order)
    * valid
    * resp
    * id
    * user
* R consists of (in order)
    * valid
    * resp
    * data
    * last
    * id
    * user
