// See LICENSE for license details.

package examples

import chisel3._
import dsptools.Truncate
import dsptools.numbers.DspComplex
import firrtl.ir.IntWidth
import spire.algebra.Ring

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

class Demod[T <: Data:Ring](gen: => T, p: DemodParams) {
  class DemodIO(numberOfOutputs: Int) extends Bundle {
    // Input either signed DSPFixed or DSPDbl, as set by gen
    val symbolIn = DspComplex(gen.asInput, gen.asInput)
    // # of "hard" bits required is set by the maximum n-QAM supported
    // (toBitWidth converts from an integer to # of bits required to represent it)
    // Note for 4-QAM, the UInt range is [0,3]
    // For Fixed, output notation is Qn.m (width = n + m + 1 for sign bit)
    // When performing hard decoding, n,m = 0, so each element of the vec should
    // only be 1 bit wide (using the sign bit)
    // When performing hard decoding, n = 0, m = ??? -- you should determine what ??? is (15 is a placeholder)
    val m = if (p.softDemod) 15 else 0
    val demodOut = Vec(numberOfOutputs, Bool(OUTPUT))
//    val demodOut = Vec(UInt.toBitWidth(p.QAMn.max-1), Bool(OUTPUT))
    // If the bits of demodOut are interpreted as signed BigInt rather than fixed (i.e. renormalize wrt LSB),
    // a large positive number is a very confident 0, and a small positive number is a less confident 0.
    // Negative #'s are associated with confidence for being a 1. We chose the sign of the LLR as positive <-> 0
    // bit because then the 2's complement sign bit of the LLR is the same as the hard decoder decision.
    // People aren't generally consistent about choosing positive LLRs to correspond to 0 or 1, so we choose
    // one with a convenient interpretation in this context.
    // Offset of the input sample relative to frame size (needs to support up to max frame size)
    val offsetIn = UInt(INPUT,p.frameSizes.max-1)
    // If symbolIn --> corresponding demodOut takes n cycles, offsetOut should be offsetIn delayed n clocks
    val offsetOut = UInt(OUTPUT,p.frameSizes.max-1)
    val reset = Bool(INPUT)
    //Constellation type to demodulate. (2,4,16,64,256)
    val modulation_type = UInt(INPUT, p.QAMn.max)
  }

  val io = new DemodIO(gen.getWidth)

  implicit val parameters = p



  //check if the integer part of the inputs are odd
  //TODO: get toInt working so following statements can be added
//  val real_odd =  Bool(((io.symbolIn.real.toInt(Truncate)).toBits & SInt(1)) === SInt(1))
//  val imag_odd =  Bool(((io.symbolIn.imaginary.toInt(Truncate)).toBits & SInt(1)) === SInt(1))

}
