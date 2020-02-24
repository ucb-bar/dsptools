// See LICENSE for license details.

package craft

import chisel3._
import chisel3.internal.requireIsHardware
import chisel3.util._

object ShiftRegisterMem {

  // use_sp_mem = use single port SRAMs? if false, use dual-port SRAMs
  def apply[T <: Data](in: T, n: Int, en: Bool = true.B, use_sp_mem: Boolean = false, name: String = null): T =
  {
    requireIsHardware(in)
    //require(n%2 == 0, "Odd ShiftRegsiterMem not supported yet")

    if (n == 0) {
      in
    } else if (n == 1) {
      val out = RegEnable(in, en)
      out
    //} else if (use_sp_mem && n%2 == 0) { // TODO: this passes the test but doesn't work for all cases
    //  val out = Wire(in.cloneType)
    //  val mem = SyncReadMem(n/2, Vec(in, in))
    //  if (name != null) {
    //    println(s"Name support not implemented")
    //    //sram.setName(name)
    //  }
    //  val index_counter = Counter(en, n)._1
    //  val raddr = (index_counter + 2.U) >> 1.U
    //  val waddr = RegEnable(index_counter >> 1.U, (n/2-1).U, en)
    //  val wen = index_counter(0) && en
    //  val des = Reg(in.cloneType)
    //  val ser = Reg(in.cloneType)

    //  val sram_out = Reg(next=mem.read(raddr, !wen))

    //  when (wen) {
    //    mem.write(waddr, Vec(des, in))
    //    out := ser
    //  } .otherwise {
    //    des := in
    //    out := sram_out(0)
    //    ser := sram_out(1)
    //  }
    //  out
    } else {
      val mem = SyncReadMem(n, in.cloneType)
      if (name != null) {
        mem.suggestName(name)
      }
      val raddr = Counter(en, n)._1
      val out = mem.read(raddr)

      val waddr = RegEnable(raddr, (n-1).U, en) //next, init, enable
      when (en) {
        mem.write(waddr, in)
      }

      out
    }
  }
}
