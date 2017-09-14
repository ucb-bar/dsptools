package freechips.rocketchip.amba.axi4stream

import chisel3._
import chisel3.util._
import freechips.rocketchip.util.GenericParameterizedBundle

abstract class AXI4StreamBundleBase(params: AXI4StreamBundleParameters) extends GenericParameterizedBundle(params)

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

class AXI4StreamBundle(params: AXI4StreamBundleParameters) extends IrrevocableIO(new AXI4StreamBundlePayload(params)) {
  override def cloneType= new AXI4StreamBundle(params).asInstanceOf[this.type]
}
class AXI4StreamValidBundle(params: AXI4StreamBundleParameters) extends ValidIO(new AXI4StreamBundlePayload(params)) {
  override def cloneType = new AXI4StreamValidBundle(params).asInstanceOf[this.type]
}

object AXI4StreamBundle
{
  def apply(params: AXI4StreamBundleParameters) = new AXI4StreamBundle(params)
}

object AXI4StreamValidBundle
{
  def apply(params: AXI4StreamBundleParameters) = new AXI4StreamValidBundle(params)
}
