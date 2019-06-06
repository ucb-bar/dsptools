// See LICENSE for license details.

package dspblocks

import chisel3.internal.firrtl.Width
import freechips.rocketchip.amba.ahb._
import freechips.rocketchip.amba.apb._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.tilelink._

/**
  * Enumerate possible types of CSRs
  */
sealed trait CSRType

/**
  * Write-only register
  */
case object CSRControl extends CSRType

/**
  * Read-only register
  */
case object CSRStatus extends CSRType

/**
  * Custom behavior register
  */
case object CSRCustom extends CSRType

/**
  * Info associate with a CSR
  * @param tpe Type of CSR
  * @param width Width of field
  * @param init Initial value (optional)
  */
case class RegInfo(tpe: CSRType, width: Width, init: Option[BigInt])

object CSR {
  /**
    *
    */
  type Map = scala.collection.Map[CSRField, RegInfo]
}

/**
  * Base class for naming CSRs
  */
trait CSRField {
  def name: String = this.getClass.getSimpleName
}

object CSRField {
  def apply(n: String) = new CSRField { override val name = n }
  def unapply(c: CSRField): Some[String] = Some(c.name)
}

/**
  * Mixin for adding CSRs to
  */
trait HasCSR {
  def regmap(mapping: RegField.Map*)

  def addCSR(address: Int, field: Seq[RegField]): Unit = {

  }
}
/*  // implicit def csrFieldToString(in: CSRField): String = in.name
  val csrMap = scala.collection.mutable.Map[CSRField, RegInfo]()

  def addStatus(csr: CSRField, init: BigInt = 0, width: Width = 64.W): Unit = {
    csrMap += (csr -> RegInfo(CSRStatus, width, Some(init)))

  }
  def addStatus(csr: String, init: BigInt = 0, width: Width = 64.W): Unit = {
    val field = new CSRField { override val name = csr }
    csrMap += (field -> RegInfo(CSRStatus, width, Some(init)))
  }

  def addControl(csr: String, init: BigInt = 0, width: Width = 64.W): Unit = {
    val field = new CSRField { override val name = csr }
    csrMap += (field -> RegInfo(CSRControl, width, Some(init)))
  }

  def status(csr: CSRField): UInt = {
    require(csrMap(csr).tpe == CSRStatus, s"Register ${csr.name} is not a status")
    getCSRByName(csr)
  }
  def status(name: String): UInt = {
    for (c <- csrMap.keys if c.name == name) {
      require(csrMap(c).tpe == CSRStatus, s"Register $name is not a status")
    }
    getCSRByName(name)
  }

  def control(csr: CSRField): UInt = {
    require(csrMap(csr).tpe == CSRControl, s"Register ${csr.name} is not a control")
    getCSRByName(csr)
  }
  def control(name: String): UInt = {
    for (c <- csrMap.keys if c.name == name) {
      require(csrMap(c).tpe == CSRControl, s"Register $name is not a control")
    }
    getCSRByName(name)
  }
  protected def getCSRByName(csr: CSRField): UInt = getCSRByName(csr.name)
  protected def getCSRByName(name: String): UInt
}*/

trait AXI4HasCSR extends AXI4DspBlock with HasCSR {
  override val mem: Some[AXI4RegisterNode]
  override def regmap(mapping: (Int, Seq[RegField])*): Unit = mem.get.regmap(mapping:_*)
}

trait TLHasCSR extends TLDspBlock with HasCSR {
  override val mem: Some[TLRegisterNode]
  override def regmap(mapping: (Int, Seq[RegField])*): Unit = mem.get.regmap(mapping:_*)
}

trait AHBSlaveHasCSR extends AHBSlaveDspBlock with HasCSR {
  override val mem: Some[AHBRegisterNode]
  override def regmap(mapping: (Int, Seq[RegField])*): Unit = mem.get.regmap(mapping:_*)
}

trait APBHasCSR extends APBDspBlock with HasCSR {
  override val mem: Some[APBRegisterNode]
  override def regmap(mapping: (Int, Seq[RegField])*): Unit = mem.get.regmap(mapping:_*)
}
