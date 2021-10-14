// SPDX-License-Identifier: Apache-2.0

package examples

import chisel3._
import chisel3.stage.ChiselStage
import chisel3.util._
import chisel3.experimental.FixedPoint
import dsptools._
import dsptools.numbers._
import breeze.math.Complex
import org.scalatest.freespec.AnyFreeSpec
import spire.algebra.Ring
import spire.implicits._

object PassThrough{
  def apply[T<:Data:Real](  gen: T                        ):PassThrough[T] = {
    new PassThrough(        gen,                1         )
  }
  def apply[T<:Data:Real](  gen: T,             lanes:Int ):PassThrough[T] = {
    new PassThrough(        gen,                lanes     )
  }
  def apply[T<:Data:Real](  gen: DspComplex[T]            ):PassThroughComplex[T] = {
    new PassThroughComplex( gen,                1         )
  }
  def apply[T<:Data:Real](  gen: DspComplex[T], lanes:Int ):PassThroughComplex[T] = {
    new PassThroughComplex( gen,                lanes     )
  }
}
class  PassThroughComplex[T<:Data:Real](gen: DspComplex[T], lanes:Int = 1 )
  extends Module {
  val io = IO(new Bundle {
    val in  = Input(  Vec( lanes, gen ) )
    val out = Output( Vec( lanes, gen ) )
  })
  io.out := io.in
}
class  PassThrough[T<:Data:Real](gen: T, lanes:Int = 1 )
  extends Module {
  val io = IO(new Bundle {
    val in  = Input(  Vec( lanes, gen ) )
    val out = Output( Vec( lanes, gen ) )
  })
  io.out := io.in
}


class MuxVec[T<:Data:Real]( tt:T, lanes:Int = 1 )
  extends Module{
  val io = IO(new Bundle {
    val in   = Input( Vec(           lanes, tt ) )
    val addr = Input( UInt( log2Ceil(lanes).W  ) )
    val out  = Output(                      tt   )
  })
  io.out := io.in(io.addr)
}

class MuxVecComplex[T<:Data:Real]( tt:DspComplex[T], lanes:Int = 1 )
  extends Module{
  val io = IO(new Bundle {
    val in   = Input( Vec(           lanes, tt ) )
    val addr = Input( UInt( log2Ceil(lanes).W  ) )
    val out  = Output(                      tt   )
  })
  io.out := io.in(io.addr)
}

class PassThroughExamples extends AnyFreeSpec {
  "First set of examples" in {
    println(ChiselStage.emitVerilog(PassThrough(UInt(6.W))))
    println(ChiselStage.emitVerilog(PassThrough(UInt(4.W), 2)))

    println(ChiselStage.emitVerilog(PassThrough(SInt(6.W))))
    println(ChiselStage.emitVerilog(PassThrough(SInt(4.W), 3)))

    println(ChiselStage.emitVerilog(PassThrough(FixedPoint(6.W, 4.BP))))
    println(ChiselStage.emitVerilog(PassThrough(FixedPoint(6.W, 4.BP), 3)))

    println(ChiselStage.emitVerilog(PassThrough(DspComplex(SInt(8.W), SInt(4.W)))))
    println(ChiselStage.emitVerilog(PassThrough(DspComplex(SInt(8.W), SInt(4.W)), 3)))

    println(ChiselStage.emitVerilog(PassThrough(DspComplex(FixedPoint(8.W, 4.BP), FixedPoint(4.W, 2.BP)))))
    println(ChiselStage.emitVerilog(PassThrough(DspComplex(FixedPoint(8.W, 4.BP), FixedPoint(4.W, 2.BP)), 3)))

    // DspComplex with only real part
    println(ChiselStage.emitVerilog(PassThrough(DspComplex(FixedPoint(8.W, 4.BP), FixedPoint(0.W, 0.BP)))))
  }

  "second set of examples" in {
    println( ChiselStage.emitVerilog( new MuxVec(       UInt(4.W)          ) )  )
    println( ChiselStage.emitVerilog( new MuxVec(       UInt(4.W)      , 3 ) )  )
    println( ChiselStage.emitVerilog( new MuxVec( FixedPoint(6.W, 4.BP), 3 ) )  )
  }

  "third examples" in {
    println( ChiselStage.emitVerilog( new MuxVecComplex( DspComplex( SInt(8.W),             SInt(4.W)             )    )  ))
    println( ChiselStage.emitVerilog( new MuxVecComplex( DspComplex( SInt(8.W),             SInt(4.W)             ), 3 )  ))

    println( ChiselStage.emitVerilog( new MuxVecComplex( DspComplex( FixedPoint(8.W, 4.BP), FixedPoint(4.W, 2.BP) )    )  ))
    println( ChiselStage.emitVerilog( new MuxVecComplex( DspComplex( FixedPoint(8.W, 4.BP), FixedPoint(4.W, 2.BP) ), 3 )  ))
  }
}
