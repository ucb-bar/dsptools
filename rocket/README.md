Rocket DSP Utilities [![Build Status](https://travis-ci.org/ucb-art/rocket-dsp-utils.svg?branch=master)](https://travis-ci.org/ucb-art/rocket-dsp-utils)
=======================

# Overview

This project contains a set of tools useful for DSP projects involving rocket-chip.
In particular, it contains abstract classes and testers used in the CRAFT project for DSP blocks and chains.
It also contains DSP junctions (our version of AXI4-Stream) and the SAM (streaming-to-AXI4 memory).
The SAM is used at the end of a DSP chain to buffer data for readout by Rocket.
Note this project depends on things like Chisel, FIRRTL, testchipip, and rocket-chip, so it does not compile or run by itself.
See the [dsp-framework](https://github.com/ucb-art/dsp-framework) and [craft2-chip](https://github.com/ucb-art/craft2-chip) repositories for more information.

# Usage

* [SAM documentation](/doc/SAM.md)
* [JTAG documentation](/doc/JTAG.md)
* [DSP Streaming Interface documentation](/doc/stream.md)
