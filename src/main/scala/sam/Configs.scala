package sam

import breeze.math.{Complex}
import breeze.signal.{fourierTr}
import breeze.linalg._
import chisel3._
import chisel3.util._
import chisel3.iotesters._
import craft._
import firrtl_interpreter.InterpreterOptions
import dspblocks._
import dsptools.numbers.{DspReal, SIntOrder, SIntRing}
import dsptools.{DspContext, DspTester, Grow}
import org.scalatest.{FlatSpec, Matchers}
import dsptools.numbers.implicits._
import dsptools.numbers.{DspComplex, Real}
import scala.util.Random
import scala.math._
import org.scalatest.Tag

import cde._
import diplomacy._
import junctions._
import uncore.tilelink._
import uncore.coherence._

import dsptools._
import dspjunctions._
import scala.collection.mutable.Map

trait HasIPXACTParameters {
  def getIPXACTParameters: Map[String, String]
}

// create a new DSP Configuration
object SAMConfigBuilder {
  def apply(id: String, wordSize: Int, samConfig: SAMConfig): Config =
    ConfigBuilder.genParams(id, 1, () => UInt(wordSize.W)) ++
    new Config(
      (pname, site, here) => pname match {
        case SAMKey => samConfig
        case IPXACTParameters(_id) if _id == id => {
          val parameterMap = Map[String, String]()
      
          // Conjure up some IPXACT synthsized parameters.
          parameterMap ++= List(("InputLanes", site(GenKey(site(DspBlockId))).lanesIn.toString))
          parameterMap ++= List(("InputTotalBits", site(DspBlockKey(site(DspBlockId))).inputWidth.toString))
      
          parameterMap
        }
        case _ => throw new CDEMatchError
      })
  def standalone(id: String, wordSize: Int, samConfig: SAMConfig): Config =
    ConfigBuilder.buildDSP(id, {implicit p => LazyModule(new LazySAM())}) ++
    apply(id, wordSize, samConfig)
}

case class SAMKey(id: String) extends Field[SAMConfig]

// subpackets = basically how many cycles it takes for sync to repeat
// bufferDepth = how many packets to store
case class SAMConfig(subpackets: Int, bufferDepth: Int, baseAddr: Int) {
  // sanity checks
  val memDepth = subpackets*bufferDepth
  val memAddrBits = log2Up(memDepth)
}

