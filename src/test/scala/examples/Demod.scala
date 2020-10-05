// SPDX-License-Identifier: Apache-2.0

package examples

import chisel3._
import chisel3.util.log2Ceil
import dsptools.numbers._

//scalastyle:off magic.number

case class DemodParams (
  QAMn:       List[Int] = List(5),      // List of supported n-QAM i.e. 4-QAM (QPSK), 16-QAM, 64-QAM, etc.2,4,16,64,256
  frameSizes: List[Int] = List(1024),   // Supported frame sizes (see FFT sizes needed)
  softDemod:  Boolean   = false,        // If true, should do LLR calc, otherwise hard demod

  //Default graycode till 64QAM was taken from http://ecee.colorado.edu/~ecen4242/wlana/wireless802.11a.html.
  graycode_bpsk:    List[Int] = List(0,1),
  graycode_qpsk :   List[Int] = List(0,1),
  graycode_16_QAM : List[Int] = List(0, 2, 3, 1),
  graycode_64_QAM:  List[Int] = List(0, 4, 6, 2, 3, 7, 5, 1),
  //user-specified graycode for 256-QAM that can be modified.
  graycode_256_QAM: List[Int] = List(0, 1, 3, 2, 6, 7, 5, 4, 12, 13, 15, 14, 10, 11, 9, 8)
)

class Demod[T <: Data:RealBits](gen: => T, p: DemodParams) extends Module {
  class DemodIO(numberOfOutputs: Int) extends Bundle {
    // Input either signed DSPFixed or DSPDbl, as set by gen
    val symbolIn = Input(DspComplex(gen, gen))
    // # of "hard" bits required is set by the maximum n-QAM supported
    // (toBitWidth converts from an integer to # of bits required to represent it)
    // Note for 4-QAM, the UInt range is [0,3]
    // For Fixed, output notation is Qn.m (width = n + m + 1 for sign bit)
    // When performing hard decoding, n,m = 0, so each element of the vec should
    // only be 1 bit wide (using the sign bit)
    // When performing hard decoding, n = 0, m = ??? -- you should determine what ??? is (15 is a placeholder)
    val m = if (p.softDemod) 15 else 0
    val demodOut = Output(Vec(numberOfOutputs, Bool()))
//    val demodOut = Vec(UInt.toBitWidth(p.QAMn.max-1), Bool(OUTPUT))
    // If the bits of demodOut are interpreted as signed BigInt rather than fixed (i.e. renormalize wrt LSB),
    // a large positive number is a very confident 0, and a small positive number is a less confident 0.
    // Negative #'s are associated with confidence for being a 1. We chose the sign of the LLR as positive <-> 0
    // bit because then the 2's complement sign bit of the LLR is the same as the hard decoder decision.
    // People aren't generally consistent about choosing positive LLRs to correspond to 0 or 1, so we choose
    // one with a convenient interpretation in this context.
    // Offset of the input sample relative to frame size (needs to support up to max frame size)
    val offsetIn = Input(UInt(log2Ceil(p.frameSizes.max + 1).W))
    // If symbolIn --> corresponding demodOut takes n cycles, offsetOut should be offsetIn delayed n clocks
    val offsetOut = Output(UInt(log2Ceil(p.frameSizes.max + 1).W))
    val reset = Input(Bool())
    //Constellation type to demodulate. (2,4,16,64,256)
    val modulation_type = Input(UInt(log2Ceil(p.QAMn.max + 1).W))
  }

  val io = IO(new DemodIO(gen.getWidth))

  implicit val parameters = p



  //check if the integer part of the inputs are odd
  val real_odd = io.symbolIn.real.intPart.isOdd
  val imag_odd = io.symbolIn.imag.intPart.isOdd 

  //Set up the vector for each constellation.
  val bpsk_out0 = Bool()
  val qpsk_out = Vec(2, Bool())
  val QAM16_out = Vec(4, Bool())
  val QAM64_out = Vec(6, Bool())
  val QAM256_out = Vec(8, Bool())

//  //Depending on the elements of QAMn, corresponding LUTs are initated and addresses are put in.
//  //The output of the LUT is then stored in a vector.
//  if (p.QAMn.indexOf(2) != -1) { //if support BPSK
//  val LUT_bpsk= DSPModule(new IntLUT2Bools(p.graycode_bpsk,2))
//    LUT_bpsk.io.addr(0) := DSPUInt(((index_offset + real_input) >> 1), LUT_bpsk.io.addr(0).getRange.max)
//    bpsk_out0 := (LUT_bpsk.io.dout(0))(0) ? (mod_type === DSPUInt(2))
//  }
//  if (p.QAMn.indexOf(4) != -1) { //if support QPSK
//  val LUT_qpsk= DSPModule(new IntLUT2Bools(p.graycode_qpsk,2))
//    LUT_qpsk.io.addr(0) := DSPUInt(((index_offset + real_input) >> 1), LUT_qpsk.io.addr(0).getRange.max)
//    LUT_qpsk.io.addr(1) :=  DSPUInt(((index_offset + imag_input) >> 1), LUT_qpsk.io.addr(0).getRange.max)
//    qpsk_out(0) := (LUT_qpsk.io.dout(0))(0) ? (mod_type === DSPUInt(4))
//    qpsk_out(1) := (LUT_qpsk.io.dout(1))(0) ? (mod_type === DSPUInt(4))
//  }
//  if (p.QAMn.indexOf(16) != -1) { //if support 16_QAM
//  val LUT_16_QAM= DSPModule(new IntLUT2Bools(p.graycode_16_QAM,2))
//    LUT_16_QAM.io.addr(0) := DSPUInt(((index_offset + real_input) >> 1), LUT_16_QAM.io.addr(0).getRange.max)
//    LUT_16_QAM.io.addr(1) :=  DSPUInt(((index_offset + imag_input) >> 1), LUT_16_QAM.io.addr(0).getRange.max)
//    QAM16_out(0) := (LUT_16_QAM.io.dout(0))(0) ? (mod_type === DSPUInt(16))
//    QAM16_out(1) := (LUT_16_QAM.io.dout(0))(1) ? (mod_type === DSPUInt(16))
//    QAM16_out(2) := (LUT_16_QAM.io.dout(1))(0) ? (mod_type === DSPUInt(16))
//    QAM16_out(3) := (LUT_16_QAM.io.dout(1))(1) ? (mod_type === DSPUInt(16))
//  }
//  if (p.QAMn.indexOf(64) != -1) { //if support 64_QAM
//  val LUT_64_QAM= DSPModule(new IntLUT2Bools(p.graycode_64_QAM,2))
//    LUT_64_QAM.io.addr(0) := DSPUInt(((index_offset + real_input) >> 1), LUT_64_QAM.io.addr(0).getRange.max)
//    LUT_64_QAM.io.addr(1) :=  DSPUInt(((index_offset + imag_input) >> 1), LUT_64_QAM.io.addr(0).getRange.max)
//    QAM64_out(0) := (LUT_64_QAM.io.dout(0))(0) ? (mod_type === DSPUInt(64))
//    QAM64_out(1) := (LUT_64_QAM.io.dout(0))(1) ? (mod_type === DSPUInt(64))
//    QAM64_out(2) := (LUT_64_QAM.io.dout(0))(2) ? (mod_type === DSPUInt(64))
//    QAM64_out(3) := (LUT_64_QAM.io.dout(1))(0) ? (mod_type === DSPUInt(64))
//    QAM64_out(4) := (LUT_64_QAM.io.dout(1))(1) ? (mod_type === DSPUInt(64))
//    QAM64_out(5) := (LUT_64_QAM.io.dout(1))(2) ? (mod_type === DSPUInt(64))
//  }
//  if (p.QAMn.indexOf(256) != -1) { //if support 256_QAM
//  val LUT_256_QAM= DSPModule(new IntLUT2Bools(p.graycode_256_QAM,2))
//    LUT_256_QAM.io.addr(0) := DSPUInt(((index_offset + real_input) >> 1), LUT_256_QAM.io.addr(0).getRange.max)
//    LUT_256_QAM.io.addr(1) :=  DSPUInt(((index_offset + imag_input) >> 1), LUT_256_QAM.io.addr(0).getRange.max)
//    QAM256_out(0) := (LUT_256_QAM.io.dout(0))(0) ? (mod_type === DSPUInt(256))
//    QAM256_out(1) := (LUT_256_QAM.io.dout(0))(1) ? (mod_type === DSPUInt(256))
//    QAM256_out(2) := (LUT_256_QAM.io.dout(0))(2) ? (mod_type === DSPUInt(256))
//    QAM256_out(3) := (LUT_256_QAM.io.dout(0))(3) ? (mod_type === DSPUInt(256))
//    QAM256_out(4) := (LUT_256_QAM.io.dout(1))(0) ? (mod_type === DSPUInt(256))
//    QAM256_out(5) := (LUT_256_QAM.io.dout(1))(1) ? (mod_type === DSPUInt(256))
//    QAM256_out(6) := (LUT_256_QAM.io.dout(1))(2) ? (mod_type === DSPUInt(256))
//    QAM256_out(7) := (LUT_256_QAM.io.dout(1))(3) ? (mod_type === DSPUInt(256))
//  }
//
//  //Setting the appropriate output of the demodulator corresponding to the constellation type.
//  io.demodOut(0) := bpsk_out0 ? (mod_type === DSPUInt(2)) | qpsk_out(0) ? (mod_type === DSPUInt(4)) | QAM16_out(0) ? (mod_type === DSPUInt(16)) | QAM64_out(0) ? (mod_type === DSPUInt(64)) | QAM256_out(0) ? (mod_type === DSPUInt(256))
//  if (p.QAMn.max >= 4) {
//    io.demodOut(1) := qpsk_out(1) ? (mod_type === DSPUInt(4)) | QAM16_out(1)? (mod_type === DSPUInt(16)) | QAM64_out(1)? (mod_type === DSPUInt(64)) | QAM256_out(1) ? (mod_type === DSPUInt(256))
//  }
//  if (p.QAMn.max >= 16) {
//    io.demodOut(2) := QAM16_out(2)? (mod_type === DSPUInt(16)) | QAM64_out(2)? (mod_type === DSPUInt(64)) | QAM256_out(2) ? (mod_type === DSPUInt(256))
//    io.demodOut(3) := QAM16_out(3)? (mod_type === DSPUInt(16)) | QAM64_out(3)? (mod_type === DSPUInt(64)) | QAM256_out(3) ? (mod_type === DSPUInt(256))
//  }
//  if (p.QAMn.max >= 64) {
//    io.demodOut(4) := QAM64_out(4)? (mod_type === DSPUInt(64)) | QAM256_out(4) ? (mod_type === DSPUInt(256))
//    io.demodOut(5) := QAM64_out(5)? (mod_type === DSPUInt(64)) | QAM256_out(5) ? (mod_type === DSPUInt(256))
//  }
//  if (p.QAMn.max >= 256) {
//    io.demodOut(6) := QAM256_out(6)? (mod_type === DSPUInt(256))
//    io.demodOut(7) := QAM256_out(7)? (mod_type === DSPUInt(256))
//  }
//
//  io.offsetOut := io.offsetIn
//  debug(real_odd)
//  debug(imag_odd)
//  debug(real_input_unclamped)
//  debug(imag_input_unclamped)
//  debug(real_input)
//  debug(imag_input)
//  debug(index_real)
//  debug(index_imag)
//  debug(index_offset)
//  debug(io.demodOut)
//  debug(real_odd)
}
