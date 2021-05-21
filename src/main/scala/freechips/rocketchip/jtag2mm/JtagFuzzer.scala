// SPDX-License-Identifier: Apache-2.0

package freechips.rocketchip.jtag2mm

import chisel3._
import chisel3.experimental._
import chisel3.util._
import chisel3.util.random.LFSR


class InvertedJtagIO extends Bundle {
  // TRST (4.6) is optional and not currently implemented.
  val TCK = Output(Bool())
  val TMS = Output(Bool())
  val TDI = Output(Bool())
  val TDO = Input(new Bool())
}

class InvertedJtagIOPlus extends InvertedJtagIO {
  val finished = Output(Bool())
}

class JtagFuzzer(irLength: Int, beatBytes: Int, numOfTransfers: Int) extends Module {

  val io = IO(new InvertedJtagIOPlus)
  
  object State extends ChiselEnum {
    val sIdle, sTCK, sTMS, sTCKandTMS, sNone, sDataTCK, sDataTMS, sDataTCKandTMS, sDataNone  = Value
  }
  
  val lfsrAddrReg = RegInit(UInt(16.W), 0.U)
  lfsrAddrReg := LFSR(14) << 2
  val lfsrDataReg = RegInit(UInt(32.W), 0.U)
  lfsrDataReg := LFSR(32)
  
  val dataBitCounter = RegInit(UInt(8.W), 0.U)
  
  val idleCycleCounter = RegInit(UInt(4.W), 0.U)
  
  val transferCounter = RegInit(UInt(4.W), 0.U)
  
  io.finished := Mux(transferCounter >= numOfTransfers.U, true.B, false.B)

  val state = RegInit(State.sIdle)
  val stateCounter = RegInit(UInt(10.W), 0.U)
  when(state =/= RegNext(state)) {
    stateCounter := stateCounter + 1.U
  }
  
  io.TCK := DontCare
  io.TMS := DontCare
  io.TDI := DontCare
  
  switch(state) {
    is(State.sIdle) {
      when((idleCycleCounter === 10.U) && (transferCounter < numOfTransfers.U)){
        state := State.sTMS
      }
      io.TCK := false.B
      io.TMS := false.B
      io.TDI := false.B
      idleCycleCounter := idleCycleCounter + 1.U
      stateCounter := 0.U
      dataBitCounter := 0.U
    }
    is(State.sTMS) {
      // jtag init
      when((stateCounter === 0.U) || (stateCounter === 2.U) || (stateCounter === 4.U) || (stateCounter === 6.U) || (stateCounter === 8.U)) {
        state := State.sTCKandTMS
      }
      // first instruction
      .elsewhen((stateCounter === 12.U) || (stateCounter === 14.U) || (stateCounter === 28.U)){
        state := State.sTCKandTMS
      }
      // first data
      .elsewhen((stateCounter === 32.U) || (stateCounter === 70.U)){
        state := State.sTCKandTMS
      }
      // second instruction
      .elsewhen((stateCounter === 74.U) || (stateCounter === 76.U) || (stateCounter === 90.U)){
        state := State.sTCKandTMS
      }
      // second data
      .elsewhen((stateCounter === 94.U) || (stateCounter === 164.U)){
        state := State.sTCKandTMS
      }
      // third instruction
      .elsewhen((stateCounter === 168.U) || (stateCounter === 170.U) || (stateCounter === 184.U)){
        state := State.sTCKandTMS
      }
      io.TCK := false.B
      io.TMS := true.B
      io.TDI := false.B
      dataBitCounter := 0.U
      idleCycleCounter := 0.U
    }
    is(State.sTCK) {
      // endings
      when((stateCounter === 11.U) || (stateCounter === 31.U) || (stateCounter === 73.U) || (stateCounter === 93.U) || (stateCounter === 167.U)) {
        state := State.sTMS
      }
      // first instruction
      .elsewhen(stateCounter === 17.U){
        state := State.sNone
      } .elsewhen(stateCounter === 19.U) {
        state := State.sDataNone
      }
      // first data
      .elsewhen(stateCounter === 35.U){
        state := State.sNone
      } .elsewhen(stateCounter === 37.U) {
        state := State.sDataNone
      }
      // second instruction
      .elsewhen(stateCounter === 79.U){
        state := State.sNone
      } .elsewhen(stateCounter === 81.U) {
        state := State.sDataNone
      }
      // second data
      .elsewhen(stateCounter === 97.U){
        state := State.sNone
      } .elsewhen(stateCounter === 99.U) {
        state := State.sDataNone
      }
      // third instruction
      .elsewhen(stateCounter === 173.U){
        state := State.sNone
      } .elsewhen(stateCounter === 175.U) {
        state := State.sDataNone
      }
      // the end
      .elsewhen(stateCounter === 187.U){
        state := State.sIdle
        transferCounter := transferCounter + 1.U
      }
      io.TCK := true.B
      io.TMS := false.B
      io.TDI := false.B
      dataBitCounter := 0.U
    }
    is(State.sNone) {
      // jtag init
      when(stateCounter === 10.U) {
        state := State.sTCK
      }
      // first instruction
      .elsewhen((stateCounter === 16.U) || (stateCounter === 18.U) || (stateCounter === 30.U)){
        state := State.sTCK
      }
      // first data
      .elsewhen((stateCounter === 34.U) || (stateCounter === 36.U) || (stateCounter === 72.U)){
        state := State.sTCK
      }
      // second instruction
      .elsewhen((stateCounter === 78.U) || (stateCounter === 80.U) || (stateCounter === 92.U)){
        state := State.sTCK
      }
      // second data
      .elsewhen((stateCounter === 96.U) || (stateCounter === 98.U) || (stateCounter === 166.U)){
        state := State.sTCK
      }
      // third instruction
      .elsewhen((stateCounter === 172.U) || (stateCounter === 174.U) || (stateCounter === 186.U)){
        state := State.sTCK
      }
      io.TCK := false.B
      io.TMS := false.B
      io.TDI := false.B
      dataBitCounter := 0.U
    }
    is(State.sTCKandTMS) {
      // jtag init
      when((stateCounter === 1.U) || (stateCounter === 3.U) || (stateCounter === 5.U) || (stateCounter === 7.U)) {
        state := State.sTMS
      } .elsewhen (stateCounter === 9.U) {
        state := State.sNone
      }
      // first instruction
      .elsewhen((stateCounter === 13.U)){
        state := State.sTMS
      } .elsewhen((stateCounter === 15.U) || (stateCounter === 29.U)){
        state := State.sNone
      }
      //first data
      .elsewhen((stateCounter === 33.U) || (stateCounter === 71.U)){
        state := State.sNone
      }
      // second instruction
      .elsewhen((stateCounter === 75.U)){
        state := State.sTMS
      } .elsewhen((stateCounter === 77.U) || (stateCounter === 91.U)){
        state := State.sNone
      }
      //second data
      .elsewhen((stateCounter === 95.U) || (stateCounter === 165.U)){
        state := State.sNone
      }
      // third instruction
      .elsewhen((stateCounter === 169.U)){
        state := State.sTMS
      } .elsewhen((stateCounter === 171.U) || (stateCounter === 185.U)){
        state := State.sNone
      }
      io.TCK := true.B
      io.TMS := true.B
      io.TDI := false.B
      dataBitCounter := 0.U
    }
    is(State.sDataNone) {
      // first instruction
      when((stateCounter === 20.U) || (stateCounter === 22.U) || (stateCounter === 24.U)){
        state := State.sDataTCK
      } 
      // first data
      .elsewhen((stateCounter >= 38.U) && (stateCounter <= 66.U)){
        state := State.sDataTCK
      }
      // second instruction
      .elsewhen((stateCounter === 82.U) || (stateCounter === 84.U) || (stateCounter === 86.U)){
        state := State.sDataTCK
      }
      // second data
      .elsewhen((stateCounter >= 100.U) && (stateCounter <= 160.U)){
        state := State.sDataTCK
      }
      // third instruction
      .elsewhen((stateCounter === 176.U) || (stateCounter === 178.U) || (stateCounter === 180.U)){
        state := State.sDataTCK
      }
      when(((stateCounter >= 38.U) && (stateCounter <= 66.U)) || ((stateCounter >= 100.U) && (stateCounter <= 160.U))) {
        dataBitCounter := dataBitCounter + 1.U
      }
      io.TCK := false.B
      io.TMS := false.B
      when((stateCounter === 22.U) || (stateCounter === 82.U) || (stateCounter === 84.U) || (stateCounter === 176.U)){
        io.TDI := true.B
      } .elsewhen((stateCounter >= 38.U) && (stateCounter <= 66.U)){
        io.TDI := lfsrAddrReg(dataBitCounter)
      } .elsewhen((stateCounter >= 100.U) && (stateCounter <= 160.U)){
        io.TDI := lfsrDataReg(dataBitCounter)
      } .otherwise {
        io.TDI := false.B
      }
    }
    is(State.sDataTCK) {
      // first instruction
      when((stateCounter === 21.U) || (stateCounter === 23.U)){
        state := State.sDataNone
      } .elsewhen(stateCounter === 25.U){
        state := State.sDataTMS
      }
      // first data
      .elsewhen((stateCounter >= 39.U) && (stateCounter <= 65.U)){
        state := State.sDataNone
      } .elsewhen(stateCounter === 67.U){
        state := State.sDataTMS
      }
      // second instruction
      .elsewhen((stateCounter === 83.U) || (stateCounter === 85.U)){
        state := State.sDataNone
      } .elsewhen(stateCounter === 87.U){
        state := State.sDataTMS
      }
      // second data
      .elsewhen((stateCounter >= 101.U) && (stateCounter <= 159.U)){
        state := State.sDataNone
      } .elsewhen(stateCounter === 161.U){
        state := State.sDataTMS
      }
      // third instruction
      .elsewhen((stateCounter === 177.U) || (stateCounter === 179.U)){
        state := State.sDataNone
      } .elsewhen(stateCounter === 181.U){
        state := State.sDataTMS
      }
      io.TCK := true.B
      io.TMS := false.B
      when((stateCounter === 23.U) || (stateCounter === 83.U) || (stateCounter === 85.U) || (stateCounter === 177.U)){
        io.TDI := true.B
      } .elsewhen((stateCounter >= 39.U) && (stateCounter <= 67.U)){
        io.TDI := lfsrAddrReg(dataBitCounter-1.U)
      } .elsewhen((stateCounter >= 101.U) && (stateCounter <= 161.U)){
        io.TDI := lfsrDataReg(dataBitCounter)
      } .otherwise {
        io.TDI := false.B
      }
    }
    is(State.sDataTMS) {
      // first instruction
      when((stateCounter === 26.U)){
        state := State.sDataTCKandTMS
      }
      // first data
      .elsewhen((stateCounter === 68.U)){
        state := State.sDataTCKandTMS
      }
      // second instruction
      .elsewhen((stateCounter === 88.U)){
        state := State.sDataTCKandTMS
      }
      // second data
      .elsewhen((stateCounter === 162.U)){
        state := State.sDataTCKandTMS
      }
      // third instruction
      .elsewhen((stateCounter === 182.U)){
        state := State.sDataTCKandTMS
      }
      io.TCK := false.B
      io.TMS := true.B
      when(stateCounter === 68.U) {
        io.TDI := lfsrAddrReg(15)
      } .elsewhen(stateCounter === 162.U) {
        io.TDI := lfsrDataReg(31)
      } .otherwise {
        io.TDI := false.B
      }
    }
    is(State.sDataTCKandTMS) {
      // first instruction
      when((stateCounter === 27.U)){
        state := State.sTMS
      }
      // first data
      .elsewhen((stateCounter === 69.U)){
        state := State.sTMS
      }
      // second instruction
      .elsewhen((stateCounter === 89.U)){
        state := State.sTMS
      }
      // second data
      .elsewhen((stateCounter === 163.U)){
        state := State.sTMS
      }
      // third instruction
      .elsewhen((stateCounter === 183.U)){
        state := State.sTMS
      }
      io.TCK := true.B
      io.TMS := true.B
      when(stateCounter === 69.U) {
        io.TDI := lfsrAddrReg(15)
      } .elsewhen(stateCounter === 163.U) {
        io.TDI := lfsrDataReg(31)
      } .otherwise {
        io.TDI := false.B
      }
    }
  }
}
