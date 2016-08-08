// See LICENSE for license details.

package dsptools.example

import chisel3.core.{Vec, Reg}
import chisel3.{Data, Module, Bundle}
import dsptools.{Grow, DspContext}
import spire.algebra._
import spire.implicits._

class ConstantTapTransposedStreamingFIR[T <: Data](inputGenerator: T, outputGenerator: T, taps: Seq[T])
                                             (implicit ev : DspContext => Ring[T],
                                               implicit val context: DspContext) extends Module {
  implicit val ev2 = ev(context)
  val io = new Bundle {
    val input = inputGenerator
    val output = outputGenerator
  }

  val products: Seq[T] = taps.reverse.map { tap =>
    io.input * tap
  }

  val last = Reg(products.head)
  last := products.reduceLeft { (left: T, right: T) =>
    val reg = Reg(left)
    reg := left
    reg + right
  }

  io.output := last
}

class TransposedStreamingFIR[T <: Data](inputGenerator: => T, outputGenerator: => T,
                                        tapGenerator: => T, numberOfTaps: Int)
                                       (implicit ev : DspContext => Ring[T],
                                        val context: DspContext) extends Module {
  implicit val ev2 = ev(context)
  val io = new Bundle {
    val input = inputGenerator
    val output = outputGenerator
    val taps = Vec(numberOfTaps, tapGenerator).flip()
  }

  val products: Seq[T] = io.taps.reverse.map { tap: T =>
    io.input * tap
  }

  //  withContext(context.copy(overflowType = Grow)) { newContext =>
  //    val x = Module(new LUT)
  //  }

  val last = Reg(products.head)
  last := products.reduceLeft { (left: T, right: T) =>
    val reg = Reg(left)
    reg := left
    reg + right
  }

  io.output := last
}

