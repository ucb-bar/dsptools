// See LICENSE for license details.

// Original author: Stevo Bailey (stevo.bailey@berkeley.edu)
// Port by: Paul Rigge (rigge@berkeley.edu)

package dsptools.examples

import chisel3.util.{Counter, ShiftRegister, log2Up}
import chisel3.{Bool, Bundle, Data, Module, Reg, UInt, Vec, Wire, when}
import dsptools.numbers.Real
import dsptools.numbers.implicits._
import spire.algebra.Ring
import spire.math.{ConvertableFrom, ConvertableTo}

// polyphase filter bank io
class PFBIO[T<:Data](genIn: => T, genOut: => Option[T] = None,
                     windowSize: Int, parallelism: Int) extends Bundle {
  val data_in = Vec(parallelism, genIn.asInput)
  val data_out = Vec(parallelism, genOut.getOrElse(genIn).asOutput)
  val sync_in = UInt(log2Up(windowSize/parallelism))
  val overflow = Bool()
}

object sincHamming {
  def apply(size: Int, nfft: Int): Seq[Double] = Seq.tabulate(size) (i=>{
    val term1 = 0.54 - 0.46 * breeze.numerics.cos(2 * scala.math.Pi * i.toDouble / size)
    val term2 = breeze.numerics.sinc(size.toDouble / nfft - 0.5 * (size.toDouble / nfft) )
    println(term1 * term2 * 1024)
    term1 * term2 * 2*512
  })
  def apply(w: WindowConfig): Seq[Double] = sincHamming(w.outputWindowSize * w.numTaps, w.outputWindowSize)
}

class PFBFilter[T<:Data:Ring:ConvertableTo, V:ConvertableFrom](
                 genIn: => T,
                 genOut: => Option[T] = None,
                 genTap: => Option[T] = None,
                 val taps: Seq[Seq[V]]
                 //conv: V=>T
               ) extends Module {
  val io = new Bundle {
    val data_in = genIn.asInput
    val data_out = genOut.getOrElse(genIn).asOutput
    val overflow = Bool()
  }

  val delay = taps.length
  val count = Counter(taps.length)
  count.inc()
  val countDelayed = Reg(next=count.value)

  val tapsTransposed = taps.map(_.reverse).reverse.transpose.map( tap => {
    val tapsWire = Wire(Vec(tap.length, genTap.getOrElse(genIn)))
    tapsWire.zip(tap.reverse).foreach({case (t,d) => t := ConvertableTo[T].fromType(d)})
    tapsWire
  })

  val products = tapsTransposed.map(tap => tap(count.value) * io.data_in)

  val result = products.reduceLeft { (prev:T, prod:T) => ShiftRegister(prev, delay) + prod }

  io.data_out := result

}

case class WindowConfig(
                       numTaps: Int,
                       outputWindowSize: Int
                       )
case class PFBConfig(
                      windowFunc: WindowConfig => Seq[Double] = sincHamming.apply,
                      numTaps: Int = 4,
                      outputWindowSize: Int = 16,
                      parallelism: Int = 8,
                    // currently ignored
                      pipelineDepth: Int = 4,
                      useSinglePortMem: Boolean = false,
                      symmetricCoeffs: Boolean  = false,
                      useDeltaCompression: Boolean = false
                    ) {
  val window = windowFunc( WindowConfig(numTaps, outputWindowSize))
  val windowSize = window.length

  // various checks for validity
  assert(numTaps > 0, "Must have more than zero taps")
  assert(outputWindowSize > 0, "Output window must have size > 0")
  assert(outputWindowSize % parallelism == 0, "Number of parallel inputs must divide the output window size")
  assert(windowSize > 0, "PFB window must have > 0 elements")
  assert(windowSize == numTaps * outputWindowSize, "windowFunc must return a Seq() of the right size")
}

class PFB[T<:Data:Real](
                            genIn: => T,
                            genOut: => Option[T] = None,
                            genTap: => Option[T] = None,
                            val config: PFBConfig = PFBConfig()
                          ) extends Module {
  val io = new PFBIO(genIn, genOut, config.windowSize, config.parallelism)

  val groupedWindow = (0 until config.parallelism).map( lane => {
    (0 until config.outputWindowSize / config.parallelism).map( group => {
      (group * config.parallelism + lane until config.windowSize by config.outputWindowSize).map(config.window(_))
    })
  })

  val filters = groupedWindow.map( taps => Module(new PFBFilter(genIn, genOut, genTap, taps)))
  filters.zip(io.data_in).foreach( { case (filt, port) => filt.io.data_in := port } )
  filters.zip(io.data_out).foreach( { case (filt, port) => port := filt.io.data_out } )
}

