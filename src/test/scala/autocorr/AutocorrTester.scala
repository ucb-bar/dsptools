package autocorr

import amba.axi4.AXI4MasterModel
import breeze.math.Complex
import chisel3._
import chisel3.iotesters.PeekPokeTester
import chisel3.util.IrrevocableIO
import dspblocks.CSRField
import dsptools.numbers._
import freechips.rocketchip.amba.axi4stream.AXI4StreamBundlePayload

import scala.collection._
import scala.util.Random
import scala.language.implicitConversions

case class PeekPoke[T, V](
                      peek: T => V,
                      poke: V => T
                      )

class AutocorrTester[T <: Data : Ring, V](c: AutocorrBlindModule[T], overlap: BigInt = 16, apart: BigInt = 4)
  extends PeekPokeTester(c) with AXI4MasterModel[AutocorrBlindModule[T]] {
  implicit def field2String(f: CSRField): String = f.name

  val memAXI = c.io.mem(0)

  val csrs = c.outer.autocorr.csrs.addrmap
  // set depths
  axiWriteWord(csrs(CSRDepthOverlap), overlap)
  axiWriteWord(csrs(CSRDepthApart),   apart)

  axiWriteWord(csrs(CSRSetDepthOverlap), BigInt(1))
  axiWriteWord(csrs(CSRSetDepthOverlap), BigInt(0))
  axiWriteWord(csrs(CSRSetDepthApart),   BigInt(1))
  axiWriteWord(csrs(CSRSetDepthApart),   BigInt(0))

}

class AutocorrDataTester[T <: Data : Ring](c: AutocorrBlindModule[T],
                                              in: Seq[BigInt], out: mutable.ArrayBuffer[BigInt],
                                              overlap: BigInt = 16, apart: BigInt = 4)
  extends AutocorrTester(c, overlap, apart) {
  // for this tester, we're less worried about checking the io semantics and more concerned with showing that the
  // autocorrelation is doing the right mathematical operation
  // input will always be valid, output will always be ready
  val io_in: IrrevocableIO[AXI4StreamBundlePayload] = c.io.in(0)
  val io_out: IrrevocableIO[AXI4StreamBundlePayload] = c.io.out(0)
  poke(io_in.valid, 1)
  poke(io_out.ready, 1)


  in.foreach { x =>
    // TODO tlast, etc.
    poke(io_in.bits.data, x)
    if (peek(io_out.valid) != BigInt(0)) {
      out.append(peek(io_out.bits.data))
    }
    step(1)
  }
}