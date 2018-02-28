package freechips.rocketchip.amba.axi4stream

import chisel3._
import chisel3.util._
import freechips.rocketchip.util.GenericParameterizedBundle

/**
  * Base class for all AXI4Stream bundles
  * @param params
  */
abstract class AXI4StreamBundleBase(params: AXI4StreamBundleParameters) extends GenericParameterizedBundle(params)

/**
  * All fields of the AXI4 Stream interface except ready and valid
  * @param params
  */
class AXI4StreamBundlePayload(params: AXI4StreamBundleParameters) extends AXI4StreamBundleBase(params)
{
  val data = Output(UInt(params.dataBits.W))
  val strb = Output(UInt(params.strbBits.W))
  val keep = Output(UInt(params.keepBits.W))
  val last = Output(Bool())
  val id   = Output(UInt(params.i.W))
  val dest = Output(UInt(params.d.W))
  val user = Output(UInt(params.u.W))
}

/**
  * AXI4 Stream bundle with ready and valid
  * @param params
  */
class AXI4StreamBundle(val params: AXI4StreamBundleParameters) extends IrrevocableIO(new AXI4StreamBundlePayload(params)) {
  override def cloneType= new AXI4StreamBundle(params).asInstanceOf[this.type]
}

/**
  * AXI4 Stream bundle with valid only (no ready)
  * @param params
  */
class AXI4StreamValidBundle(val params: AXI4StreamBundleParameters) extends ValidIO(new AXI4StreamBundlePayload(params)) {
  override def cloneType = new AXI4StreamValidBundle(params).asInstanceOf[this.type]
}

object AXI4StreamBundle
{
  /**
    * Factory for making AXI4StreamBundle
    * @param params
    * @return
    */
  def apply(params: AXI4StreamBundleParameters) = new AXI4StreamBundle(params)
}

object AXI4StreamValidBundle
{
  /**
    * Factory for Making AXI4StreamValidBundle
    * @param params
    * @return
    */
  def apply(params: AXI4StreamBundleParameters) = new AXI4StreamValidBundle(params)
}
