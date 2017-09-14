// See LICENSE for license details.
package sam

import dsptools.numbers.implicits._
//import dsptools.Utilities._
import dsptools.{DspContext, Grow}
import spire.algebra.{Field, Ring}
import breeze.math.Complex
import breeze.linalg._
import breeze.signal._
import breeze.signal.support._
import breeze.signal.support.CanFilter._
import chisel3._
import chisel3.iotesters._
import firrtl_interpreter.InterpreterOptions
import org.scalatest.{FlatSpec, Matchers}
import scala.util.Random
import scala.math._
import org.scalatest.Tag
import scala.collection.mutable.ArrayBuffer

/*
import cde._
import junctions._
import uncore.tilelink._
import uncore.coherence._

import dsptools._
import dspblocks._
import diplomacy._
import craft._

object LocalTest extends Tag("edu.berkeley.tags.LocalTest")

class SAMWrapperTester(c: SAMWrapperModule)(implicit p: Parameters) extends DspBlockTester(c) {
  def doublesToBigInt(in: Seq[Double]): BigInt = {
    in.reverse.foldLeft(BigInt(0)) {case (bi, dbl) =>
      println(s"double = $dbl")
      val new_bi = BigInt(java.lang.Double.doubleToLongBits(dbl))
      (bi << 64) | new_bi
    }
  }

  val config = p(SAMKey(p(DspBlockId)))
  val gk = p(GenKey(p(DspBlockId)))
  val scr = testchipip.SCRAddressMap(c.outer.name).get

  // custom axiRead for the crossbar side
  val xbar = c.io.asInstanceOf[SAMWrapperIO].axi_out
  def xbar_ar_ready: Boolean = { (peek(xbar.ar.ready)) }
  def xbar_r_ready: Boolean = { (peek(xbar.r.valid)) }
  poke(xbar.ar.valid, 0)
  poke(xbar.r.ready, 0)
  val xbarDataWidth = xbar.w.bits.data.getWidth
  val xbarDataBytes = xbarDataWidth / 8
  val w = (ceil(p(DspBlockKey(p(DspBlockId))).inputWidth*1.0/xbarDataWidth)*xbarDataWidth).toInt
  override val maxWait = 100
  val maxData = config.memDepth*(w/xbarDataWidth)
  def xbarRead(startAddr: Int, addrCount: Int): Array[BigInt] = {

    // s_read_addr
    poke(xbar.ar.valid, 1)
    poke(xbar.ar.bits.id, 0)
    poke(xbar.ar.bits.user, 0)
    poke(xbar.ar.bits.addr, startAddr*(w/8))
    poke(xbar.ar.bits.len, (w/xbarDataWidth)*addrCount-1)
    poke(xbar.ar.bits.size, log2Ceil(xbarDataBytes))
    poke(xbar.ar.bits.lock, 0)
    poke(xbar.ar.bits.cache, 0)
    poke(xbar.ar.bits.prot, 0)
    poke(xbar.ar.bits.qos, 0)
    poke(xbar.ar.bits.region, 0)

    var waited = 0
    while (!xbar_ar_ready) {
      require(waited < maxWait, "Timeout waiting for AXI AR to be ready")
      step(1)
      waited += 1
    }

    step(1)
    poke(xbar.ar.valid, 0)
    step(1)
    poke(xbar.r.ready, 1)

    // s_read_data
    waited = 0
    while (!xbar_r_ready) {
      require(waited < maxWait, "Timeout waiting for AXI R to be valid")
      step(1)
      waited += 1
    }

    val ret = ArrayBuffer.empty[Double]

    waited = 0
    while (xbar_r_ready) {
      require(waited < maxData, "Timeout reading data from memory...too much data!")
      ret += peek(xbar.r.bits.data).toDouble
      step(1)
      waited += 1
    }
    poke(xbar.r.ready, 0)
    step(1)
    ret.toArray.grouped(w/xbarDataWidth).toArray.map(x => doublesToBigInt(x.toSeq))
  }




  // actual tests start here


  // setup input streaming data
  def streamIn = Seq(Seq.tabulate(100)(n => BigInt(n)))

  // pause stream while setting up SCR
  pauseStream

  axiWrite(scr("samWStartAddr").toInt, 1)
  axiWrite(scr("samWTargetCount").toInt, 3)
  axiWrite(scr("samWTrig").toInt, 1)
  axiWrite(scr("samWWaitForSync").toInt, 0)
  playStream
  poke(c.io.in.sync, 1)
  step(1)
  poke(c.io.in.sync, 0)
  step(20)
  pauseStream
  // packet count is basically number of sync signals received
  val pc = axiRead(scr("samWPacketCount").toInt)
  println(s"Packet count = $pc")

  val test = xbarRead(startAddr=0, addrCount=5)
  println(s"Read values:")
  // TODO: convert for floating point bits to double
  test.foreach { x =>
    println(x.toString)
  }
}

class SAMWrapperSpec extends FlatSpec with Matchers {
  behavior of "SAMWrapper"
  val manager = new TesterOptionsManager {
    testerOptions = TesterOptions(backendName = "firrtl", testerSeed = 7L)
    interpreterOptions = InterpreterOptions(setVerbose = false, writeVCD = true)
  }

  it should "work with DspBlockTester" in {
    implicit val p: Parameters = Parameters.root(ConfigBuilder.fixedPointBlockParams("sam", 1, 1, 10, 9).toInstance)
    val dut = () => {
      val lazyModule = LazyModule(new SAMWrapper)
      lazyModule.module
    }
    chisel3.iotesters.Driver.execute(dut, manager) { c => new SAMWrapperTester(c) } should be (true)
  }
}
*/