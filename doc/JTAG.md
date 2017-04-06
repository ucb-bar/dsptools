JTAG to AXI4 Interface
======================

# Overview

DspChains can optionally have another AXI master into both the data and control crossbars that are controllable via JTAG.

# Parameters

The Jtag->AXI4 master is added by the rocket parameter `DspChainIncludeJtag`.

# Implementation

The instruction register is 4 bits long.
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
| 14   |  ID code          |
| 15   |  BYPASS           |

# Usage

Each AXI channel is connected to either a source or sink JTAG chain.
Inputs for source chains consist of the fields of the AXI channel.

* AW consists of (in order)
    * addr
    * len
    * size
    * burst
    * cache
    * prot
    * qos
    * region
    * id
    * user
* W consists of (in order)
    * data
    * size
    * id
    * strb
    * user
* AR consists of (in order)
    * addr
    * len
    * size
    * burst
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
