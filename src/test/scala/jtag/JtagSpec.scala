// See LICENSE for license details.

package jtag
/*
import cde._
import chisel3.util.log2Ceil
import chisel3.iotesters._
import chisel3.iotesters.experimental._
import craft._
import diplomacy._
import dspblocks._
import jtag._
import jtag.test._

import _root_.junctions._

import org.scalatest._

sealed trait XBar
case object CtrlXBar extends XBar
case object DataXBar extends XBar

trait JtagAxiUtilities extends JtagTestUtilities {
  def awIrBase = 0
  def wIrBase  = 1
  def bIrBase  = 2
  def arIrBase = 3
  def rIrBase  = 4
  def irOffset(xbar: XBar): Int = xbar match {
    case CtrlXBar => 0
    case DataXBar => 5
  }

  def bigIntToBitString(x: BigInt, numBits: Option[Int] = None, signExt: Boolean = false): String = numBits match {
    case Some(nb) =>
      val baseStr = x.toString(2)
      val baseNumBits = baseStr.length
      require(baseNumBits <= nb, s"Value $x cannot fit into $nb number of bits")
      val bitsToAdd = nb - baseNumBits
      val extension = if (signExt) baseStr(0).toString else "0"
      extension * bitsToAdd + baseStr
    case None =>
      val minNumBits = log2Ceil(x)
      bigIntToBitString(x, Some(minNumBits))
  }

  def intToBitString(x: Int, numBits: Option[Int] = None, signExt: Boolean = false) : String =
    bigIntToBitString(BigInt(x), numBits, signExt)

  def axiWriteAddress(addr: BigInt, len: Int = 0, size: Int = 3, burst: Int = 1, lock: Boolean = true, cache: Int = 0, prot: Int = 0, qos: Int = 0, region: Int = 0, id: Int = 0, user: Int = 0)(implicit n: HasNastiParameters): String = {
    val strAddr   = bigIntToBitString(addr, Some(n.nastiXAddrBits))
    val strLen    = intToBitString(len, Some(n.nastiXLenBits))
    val strSize   = intToBitString(size, Some(n.nastiXSizeBits))
    val strBurst  = intToBitString(burst, Some(n.nastiXBurstBits))
    val strLock   = intToBitString(if (lock) 1 else 0, Some(1))
    val strCache  = intToBitString(cache, Some(n.nastiXCacheBits))
    val strProt   = intToBitString(prot, Some(n.nastiXProtBits))
    val strQos    = intToBitString(qos, Some(n.nastiXQosBits))
    val strRegion = intToBitString(region, Some(n.nastiXRegionBits))
    val strId     = intToBitString(id, Some(n.nastiWIdBits))
    val strUser   = intToBitString(user, Some(n.nastiAWUserBits))

    strAddr + strLen + strSize + strBurst + strLock + strCache + strProt +
      strQos + strRegion + strId + strUser
  }

  def axiWriteData(data: BigInt, last: Boolean = true, id: Option[Int] = None, strb: Option[Int] = None, user: Option[Int] = None)(implicit n: HasNastiParameters): String = {
    val strData = bigIntToBitString(data, Some(n.nastiXDataBits))
    val strLast = if (last) "1" else "0"
    val strId   = id match {
      case Some(i) => intToBitString(i, Some(n.nastiWIdBits))
      case None    => "0" * n.nastiWIdBits
    }
    val strStrb = strb match {
      case Some(s) => intToBitString(s, Some(n.nastiWStrobeBits))
      case None    => "1" * n.nastiWStrobeBits
    }
    val strUser = user match {
      case Some(u) => intToBitString(u, Some(n.nastiAWUserBits))
      case None    => "0" * n.nastiAWUserBits
    }
    strData + strLast + strId + strStrb + strUser
  }

  def axiWriteResponse(resp: Option[Int] = None, id: Option[Int] = None, user: Option[Int] = None)(implicit n: HasNastiParameters): String = {
    val strValid = "1"
    val strResp = resp match {
      case Some(r) => intToBitString(r, Some(n.nastiXRespBits))
      case None    => "?" * n.nastiXRespBits
    }
    val strId = id match {
      case Some(i) => intToBitString(i, Some(n.nastiWIdBits))
      case None    => "?" * n.nastiWIdBits
    }
    val strUser = user match {
      case Some(u) => intToBitString(u, Some(n.nastiBUserBits))
      case None    => "?" * n.nastiBUserBits
    }
    strValid + strResp + strId + strUser
  }

  def axiReadData(data: Option[BigInt] = None, resp: Option[Int] = None,
    id: Option[Int] = None, user: Option[Int] = None)
  (implicit n: HasNastiParameters): String = {
    // first output is the valid bit (it better be valid!)
    val strValid = "1"

    val strResp = resp match {
      case Some(i) => intToBitString(i, Some(n.nastiXRespBits))
      case None    => intToBitString(0, Some(n.nastiXRespBits))
    }
    val strData = data match {
      case Some(d) => bigIntToBitString(d, Some(n.nastiXDataBits))
      case None    => "?" * n.nastiXDataBits
    }
    val strLast = "?"
    val strId   = id match {
      case Some(i) => intToBitString(i, Some(n.nastiRIdBits))
      case None    => "?" * n.nastiRIdBits
    }
    val strUser = user match {
      case Some(u) => intToBitString(u, Some(n.nastiARUserBits))
      case None    => "?" * n.nastiARUserBits
    }

    strValid + strResp + strData + strLast + strId + strUser
  }

  def axiReadAddress(addr: BigInt, len: Int = 0, size: Int = 3,
    burst: Int = 1, lock: Boolean = false, cache: Int = 0, prot: Int = 0,
    qos: Int = 0, region: Int = 0, id: Int = 0, user: Int = 0)
  (implicit n: HasNastiParameters): String = {
    val strAddr   = bigIntToBitString(addr, Some(n.nastiXAddrBits))
    val strLen    = intToBitString(len, Some(n.nastiXLenBits))
    val strSize   = intToBitString(size, Some(n.nastiXSizeBits))
    val strBurst  = intToBitString(burst, Some(n.nastiXBurstBits))
    val strLock   = intToBitString(if (lock) 1 else 0, Some(1))
    val strCache  = intToBitString(cache, Some(n.nastiXCacheBits))
    val strProt   = intToBitString(prot, Some(n.nastiXProtBits))
    val strQos    = intToBitString(qos, Some(n.nastiXQosBits))
    val strRegion = intToBitString(region, Some(n.nastiXRegionBits))
    val strId     = intToBitString(id, Some(n.nastiRIdBits))
    val strUser   = intToBitString(user, Some(n.nastiARUserBits))

    strAddr + strLen + strSize + strBurst + strCache + strProt +
      strQos + strRegion + strId + strUser
  }


  def axiRead(io: JtagIO, addr: BigInt, expectedData: BigInt, xbar: XBar = CtrlXBar)(implicit t: InnerTester, n: HasNastiParameters): Unit = {
    val irAr  = arIrBase + irOffset(xbar)
    val irR   = rIrBase  + irOffset(xbar)
    val strAr = axiReadAddress(addr)
    val strR  = axiReadData(data = Some(expectedData))
    import BinaryParse._
    resetToIdle(io)

    // AR
    println(s"AR:\nirAr is $irAr is $strAr")
    idleToIRShift(io)
    irShift(io, intToBitString(irAr, Some(4)).reverse, "?" * 4)
    irShiftToIdle(io)

    idleToDRShift(io)
    drShift(io, strAr.reverse, "?" * strAr.length)
    drShiftToIdle(io)

    step(100)

    // R
    println(s"R:\nirR is $irR is $strR")
    idleToIRShift(io)
    irShift(io, intToBitString(irR, Some(4)).reverse, "?" * 4)
    irShiftToIdle(io)

    idleToDRShift(io)
    drShift(io, "0" * strR.length, "?" * strR.length) //strR.reverse)
    drShiftToIdle(io)

    ()
  }

  def axiWrite(io: JtagIO, addr: BigInt, data: BigInt, id: Int = 0, xbar: XBar = CtrlXBar)(implicit t: InnerTester, n: HasNastiParameters): Unit = {
    val irAw = awIrBase + irOffset(xbar)
    val irW  = wIrBase  + irOffset(xbar)
    val irB  = bIrBase  + irOffset(xbar)
    val strAw = axiWriteAddress(addr, id = id)
    val strW  = axiWriteData(data, id = Some(id))
    val strB  = axiWriteResponse(id = None) //Some(id))

    resetToIdle(io)

    // AW
    println("AW:\nirAw is $irAw is $strAw")
    idleToIRShift(io)
    irShift(io, intToBitString(irAw, Some(4)).reverse, "?" * 4)
    irShiftToIdle(io)

    idleToDRShift(io)
    drShift(io, strAw.reverse, "?" * strAw.length)
    drShiftToIdle(io)

    step(10)

    // W
    println(s"W:\nirW is $irW\nstrW is $strW")
    idleToIRShift(io)
    irShift(io, intToBitString(irW, Some(4)).reverse, "?" * 4)
    irShiftToIdle(io)

    idleToDRShift(io)
    drShift(io, strW.reverse, "?" * strW.length)
    drShiftToIdle(io)

    step(10)

    // B
    println(s"B:\nirB is $irB\nstrB is $strB")
    idleToIRShift(io)
    irShift(io, intToBitString(irB, Some(4)).reverse, "?" * 4)
    irShiftToIdle(io)

    idleToDRShift(io)
    drShift(io, "0" * strB.length, strB.reverse)
    drShiftToIdle(io)

    resetToIdle(io)
    ()
  }
}

class JtagAxiSpec extends FlatSpec with Matchers with JtagAxiUtilities {
  val dut = {implicit p: Parameters => {
    val chain = LazyModule(new DspChainWithAXI4SInput)
    chain.ctrlBaseAddr = () => 0L
    chain.dataBaseAddr = () => 0x1000L
    chain.module
  }}

  behavior of "AXI JTAG"

  it should "get the correct idcode" in {
    val p = chainParameters(BlockConnectEverything, BlockConnectEverything)
    test(dut(p), testerBackend=VerilatorBackend) { implicit t => c =>
      c.io.jtag map { case jtag =>
        println("Reset to idle")
        resetToIdle(jtag)
        println("Idle to IR")
        idleToIRShift(jtag)
        println("Shift in the IR goodness")
        irShift(jtag, "1110".reverse, "10??")
        println("IR to Idle")
        irShiftToIdle(jtag)

        println("Idle to DR")
        idleToDRShift(jtag)
        println("Shift in the DR goodness")
        val idcode =
          "0001" +
          "0" * 15 + "1" +
          "00001000010" + "1"
        drShift(jtag, "0" * idcode.length, idcode.reverse)
        println("DR to Idle")
        drShiftToIdle(jtag)
      }
    }
  }

  it should "read uuids out of the ctrl xbar" in {
    val p = chainParameters(BlockConnectEverything, BlockConnectEverything)
    test(dut(p), testerBackend=VerilatorBackend, options = new TesterOptionsManager {
      interpreterOptions = interpreterOptions.copy(setVerbose = false, writeVCD = true)
      }) { implicit t => c =>
      implicit val n: HasNastiParameters = c.io.data_axi.ar.bits

      c.io.jtag map { j =>
        axiRead(j, BigInt(0x8L), BigInt(0x12))
        step(100)
        axiRead(j, BigInt(0x10L), BigInt(0xa))
        step(100)
        axiRead(j, BigInt(0x0L), BigInt(0x0))
      }

    }
  }

  it should "write to wrapback on the ctrl xbar" in {
    val p = chainParameters(BlockConnectEverything, BlockConnectEverything)
    test(dut(p), testerBackend=VerilatorBackend, options = new TesterOptionsManager {
      interpreterOptions = interpreterOptions.copy(setVerbose = false, writeVCD = true)
      }) { implicit t => c =>
      implicit val n: HasNastiParameters = c.io.data_axi.ar.bits

      c.io.jtag map { j =>
        println("First read 0x0")
        axiRead(j, BigInt(0x0L), BigInt(0x0))
        step(100)

        println("Write")
        axiWrite(j, BigInt(0x0L), BigInt(0x7), id = 1)
        step(100)
        axiWrite(j, BigInt(0x200L), BigInt(0x12), id = 0)
        step(100)

        println("Second read")
        axiRead(j, BigInt(0x0L), BigInt(0x7))
        step(100)

        println("First read 0x200")
        axiRead(j, BigInt(0x200L), BigInt(0x0))
        step(100)

        println("Write")
        axiWrite(j, BigInt(0x200L), BigInt(0x12))
        step(100)

        println("Second read")
        axiRead(j, BigInt(0x200L), BigInt(0x12))
        step(10)
      }
    }
  }

  it should "read out of the sams on the data xbar" in {
    val p = chainParameters(BlockConnectEverything, BlockConnectEverything)
    test(dut(p), testerBackend=VerilatorBackend, options = new TesterOptionsManager {
      interpreterOptions = interpreterOptions.copy(setVerbose = false, writeVCD = true)
      }) { implicit t => c =>
      implicit val n: HasNastiParameters = c.io.data_axi.ar.bits

      c.io.jtag map { j =>
        println("First read 0x1000")
        axiRead(j, BigInt(0x1000L), BigInt(0x0), xbar = DataXBar)
        step(10)
        axiRead(j, BigInt(0x1008L), BigInt(0x0), xbar = DataXBar)
        step(10)
        axiRead(j, BigInt(0x1010L), BigInt(0x0), xbar = DataXBar)
        step(10)
        axiRead(j, BigInt(0x1018L), BigInt(0x0), xbar = DataXBar)
        step(10)

        println("Second read 0x2000")
        axiRead(j, BigInt(0x2000L), BigInt(0x0), xbar = DataXBar)
        step(10)
        axiRead(j, BigInt(0x2008L), BigInt(0x0), xbar = DataXBar)
        step(10)
        axiRead(j, BigInt(0x2010L), BigInt(0x0), xbar = DataXBar)
        step(10)
        axiRead(j, BigInt(0x2018L), BigInt(0x0), xbar = DataXBar)
        step(10)
      }

    }
  }
}
*/