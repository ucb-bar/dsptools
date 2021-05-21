// SPDX-License-Identifier: Apache-2.0

package freechips.rocketchip.jtag2mm

import chisel3._
import chisel3.experimental._
import chisel3.util._
import freechips.rocketchip.TLToBundleBridge
//import chisel3.experimental.{withClockAndReset}

import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.tilelink._


class JtagToMasterControllerIO(irLength: Int, beatBytes: Int) extends JtagBlockIO(irLength) {
  val dataOut = Output(UInt((beatBytes * 8).W))
  val dataIn = Input(UInt((beatBytes * 8).W))
  val validIn = Input(Bool())
  val receivedIn = Output(Bool())
  val receivedEnd = Output(Bool())
}

class TopModuleIO extends Bundle {
  val jtag = new JtagIO
  val asyncReset = Input(Bool())
}

class JtagController(irLength: Int, initialInstruction: BigInt, beatBytes: Int) extends Module {
  require(irLength >= 3)

  val io = IO(new JtagToMasterControllerIO(irLength, beatBytes))

  val tdo = Wire(Bool())
  tdo := DontCare
  val tdo_driven = Wire(Bool())
  tdo_driven := DontCare
  io.jtag.TDO.data := NegativeEdgeLatch(clock, tdo)
  io.jtag.TDO.driven := NegativeEdgeLatch(clock, tdo_driven)

  val stateMachine = Module(new JtagStateMachine)
  stateMachine.io.tms := io.jtag.TMS
  val currState = stateMachine.io.currState
  io.output.state := stateMachine.io.currState
  stateMachine.io.asyncReset := io.control.fsmAsyncReset

  val irShifter = Module(CaptureUpdateChain(UInt(irLength.W)))
  irShifter.io.chainIn.shift := currState === JtagState.ShiftIR.U
  irShifter.io.chainIn.data := io.jtag.TDI
  irShifter.io.chainIn.capture := currState === JtagState.CaptureIR.U
  irShifter.io.chainIn.update := currState === JtagState.UpdateIR.U
  irShifter.io.capture.bits := "b01".U

  val updateInstruction = Wire(Bool())
  updateInstruction := DontCare

  val nextActiveInstruction = Wire(UInt(irLength.W))
  nextActiveInstruction := DontCare

  val activeInstruction = NegativeEdgeLatch(clock, nextActiveInstruction, updateInstruction)

  when(reset.asBool) {
    nextActiveInstruction := initialInstruction.U(irLength.W)
    updateInstruction := true.B
  }.elsewhen(currState === JtagState.UpdateIR.U) {
    nextActiveInstruction := irShifter.io.update.bits
    updateInstruction := true.B
  }.otherwise {
    updateInstruction := false.B
  }
  io.output.instruction := activeInstruction

  io.output.reset := currState === JtagState.TestLogicReset.U

  val drShifter = Module(CaptureUpdateChain(UInt((beatBytes * 8).W)))
  drShifter.io.chainIn.shift := currState === JtagState.ShiftDR.U
  drShifter.io.chainIn.data := io.jtag.TDI
  drShifter.io.chainIn.capture := currState === JtagState.CaptureDR.U
  drShifter.io.chainIn.update := currState === JtagState.UpdateDR.U
  drShifter.io.capture.bits := "b01".U

  val updateData = Wire(Bool())
  updateData := DontCare
  val newData = Wire(UInt((beatBytes * 8).W))
  newData := DontCare
  val currentData = NegativeEdgeLatch(clock, newData, updateData)

  when(reset.asBool) {
    newData := 0.U
    updateData := false.B
  }.elsewhen(currState === JtagState.UpdateDR.U) {
    newData := drShifter.io.update.bits
    updateData := true.B
  }.otherwise {
    updateData := false.B
  }

  io.dataOut := currentData

  val dataInReg = RegInit(UInt((beatBytes * 8).W), 0.U)
  val indicator = RegInit(Bool(), false.B)
  when(io.validIn) { dataInReg := io.dataIn }
  val counterTDO = RegInit(UInt(7.W), 0.U)
  when(RegNext(io.validIn)) { io.receivedIn := true.B }.otherwise { io.receivedIn := false.B }
  when(RegNext(RegNext(indicator)) && !RegNext(indicator)) { io.receivedEnd := true.B }.otherwise {
    io.receivedEnd := false.B
  }
  when(io.validIn && !RegNext(io.validIn)) { indicator := true.B }.elsewhen(counterTDO === (8 * beatBytes - 1).U) {
    indicator := false.B
  }
  when(indicator) { counterTDO := counterTDO + 1.U }.otherwise { counterTDO := 0.U }

  when(indicator) {
    tdo := dataInReg(counterTDO)
    tdo_driven := true.B
  }.otherwise {
    tdo := false.B
    tdo_driven := false.B
  }

}

object StateTL extends ChiselEnum {
    val sIdle, sSetDataA, sSetReadAddress, sDataForward, sSetDataABurst, sIncrementWriteBurst, sSetReadAddressBurst,
      sIncrementReadBurst, sDataForwardBurst, sDataForward2Burst = Value
}

class JTAGToMasterTL[D, U, E, O, B <: Data](irLength: Int, initialInstruction: BigInt, beatBytes: Int, burstMaxNum: Int)
    extends LazyModule()(Parameters.empty) {

  require(burstMaxNum <= 128)

  val node = Some(
    TLClientNode(
      Seq(TLClientPortParameters(Seq(TLClientParameters(name = "JTAGToMasterOut", sourceId = IdRange(0, 4)))))
    )
  )

  lazy val io = Wire(new TopModuleIO)

  lazy val module = new LazyModuleImp(this) {

    val (tl, edge) = node.get.out(0)

    val jtagClk = Wire(Clock())
    jtagClk := io.jtag.TCK.asClock

    val syncReset = Wire(Bool())
    val controller = withClockAndReset(jtagClk, syncReset) {
      Module(new JtagController(irLength, initialInstruction, beatBytes))
    }
    syncReset := controller.io.output.reset
    controller.io.jtag.TCK := io.jtag.TCK
    controller.io.jtag.TMS := io.jtag.TMS
    controller.io.jtag.TDI := io.jtag.TDI
    io.jtag.TDO := controller.io.jtag.TDO
    controller.io.control.fsmAsyncReset := io.asyncReset
    controller.io.validIn := DontCare
    controller.io.dataIn := DontCare

    val state = RegInit(StateTL.sIdle)
    
    val currentInstruction = RegInit(UInt(irLength.W), initialInstruction.U)
    currentInstruction := controller.io.output.instruction

    val dataValue = RegInit(UInt((beatBytes * 8).W), 0.U)
    val addressValue = RegInit(UInt((beatBytes * 4).W), 0.U)

    val burstTotalNumber = RegInit(UInt(8.W), 0.U)
    val burstCurrentNumber = RegInit(UInt(8.W), 0.U)
    val dataValueBurst = RegInit(VecInit(Seq.fill(burstMaxNum)(0.U((beatBytes * 8).W))))

    when(currentInstruction === "b0011".U) {
      dataValue := controller.io.dataOut
    }

    when(currentInstruction === "b0010".U) {
      addressValue := controller.io.dataOut >> (beatBytes * 4)
    }

    when(currentInstruction === "b1000".U) {
      burstTotalNumber := controller.io.dataOut >> ((beatBytes - 1) * 8)
    }

    when(currentInstruction === "b1010".U) {
      burstCurrentNumber := controller.io.dataOut >> ((beatBytes - 1) * 8)
    }

    when(currentInstruction === "b1011".U) {
      dataValueBurst(burstCurrentNumber) := controller.io.dataOut
    }

    val shouldWrite = RegInit(Bool(), false.B)
    when((currentInstruction === "b0001".U) && (RegNext(currentInstruction) =/= "b0001".U)) {
      shouldWrite := true.B
    }.elsewhen(state === StateTL.sSetDataA) {
      shouldWrite := false.B
    }

    val shouldRead = RegInit(Bool(), false.B)
    when((currentInstruction === "b0100".U) && (RegNext(currentInstruction) =/= "b0100".U)) {
      shouldRead := true.B
    }.elsewhen(state === StateTL.sSetReadAddress) {
      shouldRead := false.B
    }

    val shouldWriteBurst = RegInit(Bool(), false.B)
    when(
      (currentInstruction === "b1001".U) && (RegNext(currentInstruction) =/= "b1001".U) && (burstTotalNumber > 0.U)
    ) {
      shouldWriteBurst := true.B
    }.elsewhen(state === StateTL.sSetDataABurst) {
      shouldWriteBurst := false.B
    }

    val shouldReadBurst = RegInit(Bool(), false.B)
    when(
      (currentInstruction === "b1100".U) && (RegNext(currentInstruction) =/= "b1100".U) && (burstTotalNumber > 0.U)
    ) {
      shouldReadBurst := true.B
    }.elsewhen(state === StateTL.sSetReadAddressBurst) {
      shouldReadBurst := false.B
    }

    val readData = RegInit(UInt((beatBytes * 8).W), 0.U)
    val received = RegInit(Bool(), false.B)

    val burstCounter = RegInit(UInt(8.W), 0.U)

    val size = if (beatBytes == 4) 2 else 3
    val mask = if (beatBytes == 4) 15 else 255

    switch(state) {
      is(StateTL.sIdle) {
        when(shouldWrite) {
          state := StateTL.sSetDataA
        }.elsewhen(shouldRead) {
          state := StateTL.sSetReadAddress
        }.elsewhen(shouldWriteBurst) {
          state := StateTL.sSetDataABurst
        }.elsewhen(shouldReadBurst) {
          state := StateTL.sSetReadAddressBurst
        }

        tl.d.ready := false.B
        tl.a.valid := false.B

        tl.a.bits.opcode := 0.U
        tl.a.bits.param := 0.U
        tl.a.bits.size := size.U
        tl.a.bits.source := 0.U
        tl.a.bits.address := 0.U
        tl.a.bits.mask := mask.U
        tl.a.bits.data := 0.U

        controller.io.validIn := false.B
        controller.io.dataIn := 0.U

        burstCounter := 0.U
        received := false.B
      }
      is(StateTL.sSetDataA) {
        when(tl.d.valid) {
          state := StateTL.sIdle
        }

        tl.d.ready := true.B
        tl.a.valid := true.B

        tl.a.bits.opcode := 0.U
        tl.a.bits.param := 0.U
        tl.a.bits.size := size.U
        tl.a.bits.source := 0.U
        tl.a.bits.address := addressValue
        tl.a.bits.mask := mask.U
        tl.a.bits.data := dataValue

        controller.io.validIn := false.B
        controller.io.dataIn := 0.U
      }
      is(StateTL.sSetReadAddress) {
        when(tl.d.valid) {
          state := StateTL.sDataForward
        }

        tl.d.ready := true.B
        tl.a.valid := true.B

        tl.a.bits.opcode := 4.U
        tl.a.bits.param := 0.U
        tl.a.bits.size := size.U
        tl.a.bits.source := 0.U
        tl.a.bits.address := addressValue
        tl.a.bits.mask := mask.U
        tl.a.bits.data := 0.U

        readData := tl.d.bits.data

        controller.io.validIn := false.B
        controller.io.dataIn := 0.U
      }
      is(StateTL.sDataForward) {
        when(controller.io.receivedIn) {
          state := StateTL.sIdle
        }

        tl.d.ready := false.B
        tl.a.valid := false.B

        tl.a.bits.opcode := 0.U
        tl.a.bits.param := 0.U
        tl.a.bits.size := size.U
        tl.a.bits.source := 0.U
        tl.a.bits.address := 0.U
        tl.a.bits.mask := mask.U
        tl.a.bits.data := 0.U

        controller.io.validIn := true.B
        controller.io.dataIn := readData
      }

      is(StateTL.sSetDataABurst) {
        when(tl.d.valid) {
          state := StateTL.sIncrementWriteBurst
        }

        tl.d.ready := true.B
        tl.a.valid := true.B

        tl.a.bits.opcode := 0.U
        tl.a.bits.param := 0.U
        tl.a.bits.size := size.U
        tl.a.bits.source := 0.U
        tl.a.bits.address := addressValue + beatBytes.U * burstCounter
        tl.a.bits.mask := mask.U
        tl.a.bits.data := dataValueBurst(burstCounter)

        controller.io.validIn := false.B
        controller.io.dataIn := 0.U
      }
      is(StateTL.sIncrementWriteBurst) {
        when(burstCounter < (burstTotalNumber - 1.U)) {
          state := StateTL.sSetDataABurst
        }.otherwise {
          state := StateTL.sIdle
        }
        tl.d.ready := false.B
        tl.a.valid := false.B

        tl.a.bits.opcode := 0.U
        tl.a.bits.param := 0.U
        tl.a.bits.size := size.U
        tl.a.bits.source := 0.U
        tl.a.bits.address := 0.U
        tl.a.bits.mask := mask.U
        tl.a.bits.data := 0.U

        controller.io.validIn := false.B
        controller.io.dataIn := 0.U

        burstCounter := burstCounter + 1.U
      }
      is(StateTL.sSetReadAddressBurst) {
        when(tl.d.valid) {
          state := StateTL.sIncrementReadBurst
        }

        tl.d.ready := true.B
        tl.a.valid := true.B

        tl.a.bits.opcode := 4.U
        tl.a.bits.param := 0.U
        tl.a.bits.size := size.U
        tl.a.bits.source := 0.U
        tl.a.bits.address := addressValue + beatBytes.U * burstCounter
        tl.a.bits.mask := mask.U
        tl.a.bits.data := 0.U

        readData := tl.d.bits.data
        received := false.B

        controller.io.validIn := false.B
        controller.io.dataIn := 0.U
      }
      is(StateTL.sIncrementReadBurst) {
        state := StateTL.sDataForwardBurst

        tl.d.ready := false.B
        tl.a.valid := false.B

        tl.a.bits.opcode := 4.U
        tl.a.bits.param := 0.U
        tl.a.bits.size := size.U
        tl.a.bits.source := 0.U
        tl.a.bits.address := 0.U
        tl.a.bits.mask := mask.U
        tl.a.bits.data := 0.U

        controller.io.validIn := false.B
        controller.io.dataIn := 0.U
        burstCounter := burstCounter + 1.U
      }
      is(StateTL.sDataForwardBurst) {
        when(controller.io.receivedIn) {
          state := StateTL.sDataForward2Burst
        }

        tl.d.ready := false.B
        tl.a.valid := false.B

        tl.a.bits.opcode := 0.U
        tl.a.bits.param := 0.U
        tl.a.bits.size := size.U
        tl.a.bits.source := 0.U
        tl.a.bits.address := 0.U
        tl.a.bits.mask := mask.U
        tl.a.bits.data := 0.U

        controller.io.validIn := true.B
        controller.io.dataIn := readData
      }
      is(StateTL.sDataForward2Burst) {
        when(!(controller.io.receivedEnd) && received && (burstCounter >= burstTotalNumber)) {
          state := StateTL.sIdle
        }.elsewhen(!(controller.io.receivedEnd) && received && (burstCounter < burstTotalNumber)) {
          state := StateTL.sSetReadAddressBurst
        }
        when(controller.io.receivedEnd) { received := true.B }
        controller.io.dataIn := readData
        controller.io.validIn := false.B
      }
    }

  }
}

class TLJTAGToMasterBlock(
  irLength:           Int = 4,
  initialInstruction: BigInt = BigInt("0", 2),
  beatBytes:          Int = 4,
  addresses:          AddressSet,
  burstMaxNum:        Int = 8
)(
  implicit p: Parameters)
    extends JTAGToMasterTL[TLClientPortParameters, TLManagerPortParameters, TLEdgeOut, TLEdgeIn, TLBundle](
      irLength,
      initialInstruction,
      beatBytes,
      burstMaxNum
    ) {

  require(burstMaxNum <= 128)

  val devname = "TLJTAGToMasterBlock"
  val devcompat = Seq("jtagToMaster", "radardsp")
  val device = new SimpleDevice(devname, devcompat) {
    override def describe(resources: ResourceBindings): Description = {
      val Description(name, mapping) = super.describe(resources)
      Description(name, mapping)
    }
  }

  def makeIO2(): TopModuleIO = {
    val io2: TopModuleIO = IO(io.cloneType)
    io2.suggestName("ioJTAG")
    io2 <> io
    io2
  }

  val managerParams = TLManagerParameters(
    address = Seq(addresses),
    resources = device.reg,
    regionType = RegionType.UNCACHED,
    executable = true,
    supportsArithmetic = TransferSizes(1, beatBytes),
    supportsLogical = TransferSizes(1, beatBytes),
    supportsGet = TransferSizes(1, beatBytes),
    supportsPutFull = TransferSizes(1, beatBytes),
    supportsPutPartial = TransferSizes(1, beatBytes),
    supportsHint = TransferSizes(1, beatBytes),
    fifoId = Some(0)
  )

  val ioStreamNode = BundleBridgeSink[TLBundle]()
  ioStreamNode := TLToBundleBridge(managerParams, beatBytes) := node.get
  val ioTL = InModuleBody { ioStreamNode.makeIO() }
  val ioJTAG = InModuleBody { makeIO2() }

}

object StateAXI4 extends ChiselEnum {
  val sIdle, sSetDataAndAddress, sResetCounterW, sSetReadyB, sSetReadAddress, sResetCounterR, sSetReadyR,
    sDataForward, sSetDataAndAddressBurst, sResetCounterWBurst, sSetReadyBBurst, sSetReadAddressBurst,
    sResetCounterRBurst, sSetReadyRBurst, sDataForwardBurst, sDataForward2Burst = Value
}

class JTAGToMasterAXI4(irLength: Int, initialInstruction: BigInt, beatBytes: Int, address: AddressSet, burstMaxNum: Int)
    extends LazyModule()(Parameters.empty) {

  require(burstMaxNum <= 128)

  val node = Some(AXI4MasterNode(Seq(AXI4MasterPortParameters(Seq(AXI4MasterParameters("ioAXI4"))))))

  lazy val io = Wire(new TopModuleIO)

  lazy val module = new LazyModuleImp(this) {

    val (ioNode, _) = node.get.out(0)

    val jtagClk = Wire(Clock())
    jtagClk := io.jtag.TCK.asClock

    val syncReset = Wire(Bool())
    val controller = withClockAndReset(jtagClk, syncReset) {
      Module(new JtagController(irLength, initialInstruction, beatBytes))
    }
    syncReset := controller.io.output.reset
    controller.io.jtag.TCK := io.jtag.TCK
    controller.io.jtag.TMS := io.jtag.TMS
    controller.io.jtag.TDI := io.jtag.TDI
    io.jtag.TDO := controller.io.jtag.TDO
    controller.io.control.fsmAsyncReset := io.asyncReset
    controller.io.validIn := DontCare
    controller.io.dataIn := DontCare

    val state = RegInit(StateAXI4.sIdle)

    val currentInstruction = RegInit(UInt(irLength.W), initialInstruction.U)
    currentInstruction := controller.io.output.instruction

    val dataValue = RegInit(UInt((beatBytes * 8).W), 0.U)
    val addressValue = RegInit(UInt((beatBytes * 4).W), 0.U)

    val burstTotalNumber = RegInit(UInt(8.W), 0.U)
    val burstCurrentNumber = RegInit(UInt(8.W), 0.U)
    val dataValueBurst = RegInit(
      VecInit(Seq.fill(burstMaxNum)(0.U((beatBytes * 8).W)))
    ) //(0.asTypeOf(UInt((beatBytes * 8).W)))))

    when(currentInstruction === "b0011".U) {
      dataValue := controller.io.dataOut
    }

    when(currentInstruction === "b0010".U) {
      addressValue := controller.io.dataOut >> (beatBytes * 4)
    }

    when(currentInstruction === "b1000".U) {
      burstTotalNumber := controller.io.dataOut >> ((beatBytes - 1) * 8)
    }

    when(currentInstruction === "b1010".U) {
      burstCurrentNumber := controller.io.dataOut >> ((beatBytes - 1) * 8)
    }

    when(currentInstruction === "b1011".U) {
      dataValueBurst(burstCurrentNumber) := controller.io.dataOut
    }

    val shouldWrite = RegInit(Bool(), false.B)
    when((currentInstruction === "b0001".U) && (RegNext(currentInstruction) =/= "b0001".U)) {
      shouldWrite := true.B
    }.elsewhen(state === StateAXI4.sSetDataAndAddress) {
      shouldWrite := false.B
    }

    val shouldRead = RegInit(Bool(), false.B)
    when((currentInstruction === "b0100".U) && (RegNext(currentInstruction) =/= "b0100".U)) {
      shouldRead := true.B
    }.elsewhen(state === StateAXI4.sSetReadAddress) {
      shouldRead := false.B
    }

    val shouldWriteBurst = RegInit(Bool(), false.B)
    when(
      (currentInstruction === "b1001".U) && (RegNext(currentInstruction) =/= "b1001".U) && (burstTotalNumber > 0.U)
    ) {
      shouldWriteBurst := true.B
    }.elsewhen(state === StateAXI4.sSetDataAndAddressBurst) {
      shouldWriteBurst := false.B
    }

    val shouldReadBurst = RegInit(Bool(), false.B)
    when(
      (currentInstruction === "b1100".U) && (RegNext(currentInstruction) =/= "b1100".U) && (burstTotalNumber > 0.U)
    ) {
      shouldReadBurst := true.B
    }.elsewhen(state === StateAXI4.sSetReadAddressBurst) {
      shouldReadBurst := false.B
    }

    val readData = RegInit(UInt((beatBytes * 8).W), 0.U)
    val received = RegInit(Bool(), false.B)

    val dataSize = if (beatBytes == 4) 2 else 3

    def maxWait = 500
    val counter = RegInit(UInt(9.W), 0.U)

    val burstCounter = RegInit(UInt(8.W), 0.U)

    switch(state) {
      is(StateAXI4.sIdle) {
        when(shouldWrite) {
          state := StateAXI4.sSetDataAndAddress
        }.elsewhen(shouldRead) {
          state := StateAXI4.sSetReadAddress
        }.elsewhen(shouldWriteBurst) {
          state := StateAXI4.sSetDataAndAddressBurst
        }.elsewhen(shouldReadBurst) {
          state := StateAXI4.sSetReadAddressBurst
        }

        ioNode.aw.valid := false.B
        ioNode.w.valid := false.B
        ioNode.b.ready := false.B

        ioNode.aw.bits.addr := 0.U
        ioNode.aw.bits.size := dataSize.U
        ioNode.w.bits.data := 0.U
        ioNode.w.bits.last := false.B
        ioNode.w.bits.strb := 255.U

        ioNode.ar.valid := false.B
        ioNode.r.ready := false.B

        controller.io.validIn := false.B
        controller.io.dataIn := 0.U

        counter := 0.U
        burstCounter := 0.U
        received := false.B
      }
      is(StateAXI4.sSetDataAndAddress) {
        when(ioNode.w.ready && ioNode.aw.ready) {
          state := StateAXI4.sResetCounterW
        }.elsewhen(counter >= maxWait.U) {
          state := StateAXI4.sIdle
        }
        ioNode.aw.valid := true.B
        ioNode.w.valid := true.B
        ioNode.b.ready := false.B

        ioNode.aw.bits.addr := addressValue
        ioNode.aw.bits.size := dataSize.U
        ioNode.w.bits.data := dataValue
        ioNode.w.bits.last := true.B
        ioNode.w.bits.strb := 255.U

        controller.io.validIn := false.B
        controller.io.dataIn := 0.U

        counter := counter + 1.U
      }
      is(StateAXI4.sResetCounterW) {
        state := StateAXI4.sSetReadyB

        ioNode.aw.valid := false.B
        ioNode.w.valid := false.B
        ioNode.b.ready := false.B

        controller.io.validIn := false.B
        controller.io.dataIn := 0.U

        counter := 0.U
      }
      is(StateAXI4.sSetReadyB) {
        when(ioNode.b.valid && (ioNode.b.bits.resp === 0.U) && (ioNode.b.bits.id === ioNode.aw.bits.id)) {
          state := StateAXI4.sIdle
        }.elsewhen(counter >= maxWait.U) {
          state := StateAXI4.sIdle
        }
        ioNode.aw.valid := false.B
        ioNode.w.valid := false.B
        ioNode.b.ready := true.B

        ioNode.aw.bits.addr := 0.U
        ioNode.aw.bits.size := dataSize.U
        ioNode.w.bits.data := 0.U
        ioNode.w.bits.last := false.B
        ioNode.w.bits.strb := 255.U

        controller.io.validIn := false.B
        controller.io.dataIn := 0.U

        counter := counter + 1.U
      }
      is(StateAXI4.sSetReadAddress) {
        when(ioNode.ar.ready) {
          state := StateAXI4.sResetCounterR
        }.elsewhen(counter >= maxWait.U) {
          state := StateAXI4.sIdle
        }
        ioNode.ar.valid := true.B
        ioNode.r.ready := false.B

        ioNode.ar.bits.addr := addressValue
        ioNode.ar.bits.size := dataSize.U

        controller.io.validIn := false.B
        controller.io.dataIn := 0.U

        counter := counter + 1.U
      }
      is(StateAXI4.sResetCounterR) {
        state := StateAXI4.sSetReadyR

        ioNode.ar.valid := false.B
        ioNode.r.ready := false.B

        controller.io.validIn := false.B
        controller.io.dataIn := 0.U

        counter := 0.U
      }
      is(StateAXI4.sSetReadyR) {
        when(ioNode.r.valid && (ioNode.r.bits.resp === 0.U) && (ioNode.r.bits.id === ioNode.ar.bits.id)) {
          state := StateAXI4.sDataForward
        }.elsewhen(counter >= maxWait.U) {
          state := StateAXI4.sIdle
        }
        ioNode.ar.valid := false.B
        ioNode.r.ready := true.B

        readData := ioNode.r.bits.data

        controller.io.validIn := false.B
        controller.io.dataIn := 0.U

        counter := counter + 1.U
      }
      is(StateAXI4.sDataForward) {
        when(controller.io.receivedEnd) {
          state := StateAXI4.sIdle
        }
        controller.io.dataIn := readData
        controller.io.validIn := true.B
      }

      is(StateAXI4.sSetDataAndAddressBurst) {
        when(ioNode.w.ready && ioNode.aw.ready) {
          state := StateAXI4.sResetCounterWBurst
        }.elsewhen(counter >= maxWait.U) {
          state := StateAXI4.sIdle
        }
        ioNode.aw.valid := true.B
        ioNode.w.valid := true.B
        ioNode.b.ready := false.B

        ioNode.aw.bits.addr := addressValue + burstCounter * beatBytes.U
        ioNode.aw.bits.size := dataSize.U
        ioNode.w.bits.data := dataValueBurst(burstCounter)
        ioNode.w.bits.last := true.B
        ioNode.w.bits.strb := 255.U

        controller.io.validIn := false.B
        controller.io.dataIn := 0.U

        counter := counter + 1.U
      }
      is(StateAXI4.sResetCounterWBurst) {
        state := StateAXI4.sSetReadyBBurst

        ioNode.aw.valid := false.B
        ioNode.w.valid := false.B
        ioNode.b.ready := false.B

        controller.io.validIn := false.B
        controller.io.dataIn := 0.U

        counter := 0.U
        burstCounter := burstCounter + 1.U
      }
      is(StateAXI4.sSetReadyBBurst) {
        when(
          ioNode.b.valid && (ioNode.b.bits.resp === 0.U) && (ioNode.b.bits.id === ioNode.aw.bits.id) && (burstCounter >= burstTotalNumber)
        ) {
          state := StateAXI4.sIdle
        }.elsewhen(counter >= maxWait.U) {
          state := StateAXI4.sIdle
        }.elsewhen(
          ioNode.b.valid && (ioNode.b.bits.resp === 0.U) && (ioNode.b.bits.id === ioNode.aw.bits.id) && (burstCounter < burstTotalNumber)
        ) {
          state := StateAXI4.sSetDataAndAddressBurst
        }
        ioNode.aw.valid := false.B
        ioNode.w.valid := false.B
        ioNode.b.ready := true.B

        ioNode.aw.bits.addr := 0.U
        ioNode.aw.bits.size := dataSize.U
        ioNode.w.bits.data := 0.U
        ioNode.w.bits.last := false.B
        ioNode.w.bits.strb := 255.U

        controller.io.validIn := false.B
        controller.io.dataIn := 0.U

        counter := counter + 1.U
      }
      is(StateAXI4.sSetReadAddressBurst) {
        when(ioNode.ar.ready) {
          state := StateAXI4.sResetCounterRBurst
        }.elsewhen(counter >= maxWait.U) {
          state := StateAXI4.sIdle
        }
        ioNode.ar.valid := true.B
        ioNode.r.ready := false.B

        ioNode.ar.bits.addr := addressValue + burstCounter * beatBytes.U
        ioNode.ar.bits.size := dataSize.U

        controller.io.validIn := false.B
        controller.io.dataIn := 0.U

        counter := counter + 1.U
        received := false.B
      }
      is(StateAXI4.sResetCounterRBurst) {
        state := StateAXI4.sSetReadyRBurst

        ioNode.ar.valid := false.B
        ioNode.r.ready := false.B

        controller.io.validIn := false.B
        controller.io.dataIn := 0.U

        counter := 0.U
        burstCounter := burstCounter + 1.U
      }
      is(StateAXI4.sSetReadyRBurst) {
        when(ioNode.r.valid && (ioNode.r.bits.resp === 0.U) && (ioNode.r.bits.id === ioNode.ar.bits.id)) {
          state := StateAXI4.sDataForwardBurst
        }.elsewhen(counter >= maxWait.U) {
          state := StateAXI4.sIdle
        }
        ioNode.ar.valid := false.B
        ioNode.r.ready := true.B

        readData := ioNode.r.bits.data

        controller.io.validIn := false.B
        controller.io.dataIn := 0.U

        counter := counter + 1.U
      }
      is(StateAXI4.sDataForwardBurst) {
        when(controller.io.receivedIn) {
          state := StateAXI4.sDataForward2Burst
        }
        controller.io.dataIn := readData
        controller.io.validIn := true.B
      }
      is(StateAXI4.sDataForward2Burst) {
        when(!(controller.io.receivedEnd) && received && (burstCounter >= burstTotalNumber)) {
          state := StateAXI4.sIdle
        }.elsewhen(!(controller.io.receivedEnd) && received && (burstCounter < burstTotalNumber)) {
          state := StateAXI4.sSetReadAddressBurst
        }
        when(controller.io.receivedEnd) { received := true.B }
        controller.io.dataIn := readData
        controller.io.validIn := false.B
      }
    }
  }
}

class AXI4JTAGToMasterBlock(
  irLength:           Int = 4,
  initialInstruction: BigInt = BigInt("0", 2),
  beatBytes:          Int = 4,
  addresses:          AddressSet,
  burstMaxNum:        Int = 8
)(
  implicit p: Parameters)
    extends JTAGToMasterAXI4(irLength, initialInstruction, beatBytes, addresses, burstMaxNum) {
  require(burstMaxNum <= 128)

  def makeIO2(): TopModuleIO = {
    val io2: TopModuleIO = IO(io.cloneType)
    io2.suggestName("ioJTAG")
    io2 <> io
    io2
  }

  val slaveParams = AXI4SlaveParameters(
    address = Seq(addresses),
    regionType = RegionType.UNCACHED,
    executable = true,
    supportsWrite = TransferSizes(1, beatBytes),
    supportsRead = TransferSizes(1, beatBytes)
  )

  val ioNode = BundleBridgeSink[AXI4Bundle]()
  ioNode :=
    AXI4ToBundleBridge(AXI4SlavePortParameters(Seq(slaveParams), beatBytes)) := node.get
  val ioAXI4 = InModuleBody { ioNode.makeIO() }

  val ioJTAG = InModuleBody { makeIO2() }

}

/*object JTAGToMasterDspBlockTL extends App {
  implicit val p: Parameters = Parameters.empty
  val jtagModule = LazyModule(new TLJTAGToMasterBlock(3, BigInt("0", 2), 4, AddressSet(0x00000, 0x3fff), 8))

  chisel3.Driver.execute(args, () => jtagModule.module)
}

object JTAGToMasterDspBlockAXI4 extends App {
  implicit val p: Parameters = Parameters.empty
  val jtagModule = LazyModule(new AXI4JTAGToMasterBlock(3, BigInt("0", 2), 4, AddressSet(0x00000, 0x3fff), 8))

  chisel3.Driver.execute(args, () => jtagModule.module)
}*/
