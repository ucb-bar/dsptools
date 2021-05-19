JTAG To Memory Master (JTAG2MM) Chisel Generator
========================================================

## Overview
In this document, a generator of JTAG to memory-mapped bus master modules, written in [Chisel](http://www.chisel-lang.org) hardware design language, is described. Generated modules can initiate AXI4/TileLink(TL) transactions and drive AXI4/TL memory mapped slave(s) through an interconnect bus.

### JTAG To Memory Master
JTAG To Memory Master block can be divided into two subparts: JTAG controller and AXI4/TL controller. Its simplified block diagram with input and output interfaces is shown below.

![JTAG To Memory Master block diagram](./images/jtag2mm.svg)

### JTAG Controller

The main task of the JTAG controller is to collect signals from JTAG input serial interface and forward them to AXI4/TL controller in correct form through parallel port, as well as to acquired signals from parallel port convert to serial data and output them using JTAG output serial interface. JTAG serial interface consists of standard JTAG signals:
* `TCK` - test clock input signal, used as clock signal for JTAG controller
* `TMS` - test mode select input signal, used for state transition inside JTAG FSM
* `TDI` - test data in input signal, used to acquire data sent to the module
* `TDO_data` - test data out output data signal, used to send data to the output
* `TDO_driven` - test data out output valid signal, used to indicate that data on the output is valid
* `asyncReset` - asynchronous reset signal, used for entering reset state inside JTAG FSM

JTAG finite state machine (FSM) is a standard JTAG FSM and it is used to ensure that the JTAG controller is working properly. Through the change of states of this FSM, it is possible to acquire instruction and data values from the JTAG serial input interface. `TCK` signal is used as a clock signal, state changing happens thanks to `TMS` signal and `TDI` signal is stored as data. JTAG FSM state changing diagram is shown below.

![JTAG FSM state changing diagram](./images/jtag_fsm.svg)

JTAG controller is decsribed inside the `rocket/src/main/scala/jtag2mm/JtagToMaster.scala` scala file. JTAG FSM, JTAG IO bundles and some of the other subparts and submodules are modified versions that are taken from the repository: [chisel-jtag](https://github.com/ucb-art/chisel-jtag).

### AXI4/TL Controller
 
AXI4/TL controller is used to initiate transactions and drive signals through an interconnect bus. Unlike the JTAG controller, it is driven by the system clock signal. It receives instruction and data values from JTAG controller and acts correspondingly. It also has an FSM which ensures that write and read transactions are performed in accordance with the appropriate transfer protocol. FSM is represented in the figure below. FSM for AXI4 controller consists of eight states:
* `sIdle` - reset state, stays in this state until write or read transaction is initiated
* `sSetDataAndAddress` - state in which address is set on AW channel, data is set on W channel and valid signals are set on both AW and W channels. Stays in this state until ready signals are not valid on both W and AW channels or until a counter that ensures that FSM isn't stuck in this state counts out
* `sResetCounterW` - state in which the mentioned counter is reset. Stays in this state for exactly one clock cycle 
* `sSetReadyB` - state in which ready signal is set on acknowledgement B channel. Stays in this state until B channel valid signal is not active or until a counter that ensures that FSM isn't stuck in this state counts out
* `sSetReadAddress` - state in which address and valid signals are set on AR channel. Stays in this state until AR channel ready signal is not valid or until a counter that ensures that FSM isn't stuck in this state counts out
* `sResetCounterR` - state in which the mentioned counter is reset. Stays in this state for exactly one clock cycle 
* `sSetReadyR` - state in which ready signal is set on R channel and data is read from the same channel. Stays in this state until R channel valid signal is not active or until a counter that ensures that FSM isn't stuck in this state counts out
* `sDataForward` - state in which read data is forwarded to the JTAG controller, along with active validIn signal. Stays in this state until receivedIn signal is not active

![AXI4 FSM state changing diagram](./images/axi4_fsm.svg)

Same FSM could be applied for AXI4 burst transfers. Only difference is that after the completed single data transfer, FSM enters `sIdle` state only if the burst transfers counter has counted out. Otherwise, FSM enters `sSetDataAndAddress`/`sSetReadAddress` state to perform another transfer.

TileLink FSM has different protocol signals involved, but works on the same principles as AXI4 FSM. AXI4 and TL controllers, as well as the appropriate FSM, are decsribed inside the `rocket/src/main/scala/jtag2mm/JtagToMaster.scala` scala file.

### User manual

Total of four instructions are necessary for the JTAG2MM module to work properly. With additional instructions for performing burst data transfers, total of 9 instructions are defined. Instruction codes along with their descriptions are provided below:
* `0x01` - write instruction, initiates the AXI4/TL FSM to begin writing acquired data to acquired address
* `0x02` - address acquire instruction, accepts the serial data as the address for read/write instruction
* `0x03` - data acquire instruction, accepts the serial data as the data for read/write instruction
* `0x04` - read instruction, initiates the AXI4/TL FSM to begin reading data from the acquired address
* `0x08` - number of burst transactions acquire instruction, accepts the serial data as the number of read/write instructions during one burst transfer cycle
* `0x09` - burst write instruction, initiates the AXI4/TL FSM to begin acquired number of write transactions. Data is written to consecutive addresses
* `0x0A` - data index number acquire instruction, accepts the serial data as the index number of data to be acquired for the burst read/write transfer
* `0x0B` - indexed data acquire instruction, accepts the serial data as the data at the acquired index number for the burst read/write transfer
* `0x0C` - burst read instruction, initiates the AXI4/TL FSM to begin acquired number of read transactions. Data is read from consecutive addresses

User initiates one of defined instruction by driving the input JTAG signals with appropriate values. `TCK` signal should be driven continuously. Using `TMS` signal, JTAG FSM enters the state in which it accepts the serial data from `TDI` input as the instruction value. Address and data acquire instructions, as well as the number of burst transactions acquire, data index number acquire and indexed data acquire instructions, require data values besides address values, so after sending appropriate instruction code, by using `TMS` signal, user should enter the JTAG FSM state in which it accepts the serial data from `TDI` input as the data value. In these instructions, provided data is stored into appropriate registers, so that the write or read instruction can be performed. 
Before the write instruction, both address acquire and data acquire instructions must be performed. Before the read instruction, address acquire instruction must be performed. For burst write instruction, data for every single transaction must be acquired beforehand, as well as the total number of burst transactions for both burst write and burst read instructions. Current instruction and data values are being continuously sent from JTAG controller to AXI4/TL controller. When instruction value equals one of the acquire instructions code, obtained data value is stored in the appropriate register inside the AXI4/TL controller. When instruction value equals read, write, burst read or burst write instruction code, that's the indicator for the AXI4/TL controller to start (burst) read/write transaction on the interconnect bus. Two read/write/burst read/ burst write instructions of the same type cannot appear sequentially one right after another, there must be at least one other instruction between these two. After performing the read or burst read instruction, read data appear on the serial output JTAG `TDO` port.

## Tests

Several tests were used to verify the correctness of the module. Some of them are defined inside the `rocket/src/test/scala/jtag2mm` directory. The rest can be found in the repository: [JTAG2MM](https://github.com/milovanovic/jtag2mm). In order to verify the module, a simple streaming multiplexer module is defined for both TileLink and AXI4 interfaces. Moreover, JTAG Fuzzer module that initiate arbitrary number of TL/AXI4 write transactions when connected to JTAG2MM module is defined. These transactions' data and addresses are pseudo-random. JTAG Fuzzer module can be of great value for additional testing of JTAG2MM modules.

Following tests are defined in this repository:
* `Jtag2AXI4MultiplexerTester.scala` - test used to verify the AXI4 variant of the JTAG2MM module working with AXI4Multiplexer module with memory mapped control registers connected to the AXI4 bus.
* `Jtag2TLMultiplexerTester.scala` - test used to verify the TL variant of the JTAG2MM module working with TLMultiplexer module with memory mapped control registers connected to the TL bus.
* `JtagFuzzerTester.scala` - test used to verify the correctness of the output signals of the JTAG Fuzzer module.


