// See LICENSE for license details.

package dsptools.examples

import chisel3.util.{Counter, log2Up}
import chisel3.{Bool, Data, Mem, Mux, Reg, SeqMem, UInt, Vec, when}

// shift register implemented as an SRAM memory with internal counter
object ShiftRegisterMem {

  // When using single-ported SRAMs, you have the option of using
  //   two SRAMs with type "in" and depth "n", or one SRAM with
  //   inputs twice the width of "in" and depth "n/2". I assume you
  //   want only 1 SRAM by default.
  var use_two_srams = false

  // use_sp_mem = use single port SRAMs?
  def apply[T <: Data](in: T, n: Int, en: Bool = Bool(true), use_sp_mem: Boolean = false, name: String = null): T =
  {
    if (n%2 == 1 && use_sp_mem) {
      println("Creating a ShiftRegisterMem with an odd shift amount will use two SRAMs instead of one.")
      use_two_srams = true
    }
    if (use_sp_mem) {
      val out = in.cloneType
      if (use_two_srams) {
        val sram0 = SeqMem(n, in.cloneType)
        val sram1 = SeqMem(n, in.cloneType)
        if (name != null) {
          println(s"No name support yet")
          //sram0.setName(name + "_0")
          //sram1.setName(name + "_1")
        }
        val (index_counter, switch_sram) = Counter(en, n)
        val sram_num = Reg(init=Bool(false))
        sram_num := Mux(switch_sram, ~sram_num, sram_num)
        val reg_raddr0 = Reg(UInt())
        val reg_raddr1 = Reg(UInt())
        val reg_waddr = Reg(next=index_counter, init=UInt(n-1, log2Up(n)))
        when (en) {
          when (sram_num) {
            sram1(reg_waddr) := in
            reg_raddr0 := index_counter
          }
            .otherwise {
              sram0(reg_waddr) := in
              reg_raddr1 := index_counter
            }
        }
        // in case Mux doesn't work
        when (sram_num) {
          out := sram0(reg_raddr0)
        }
          .otherwise {
            out := sram1(reg_raddr1)
          }
        out
      }
      else {
        val sram = SeqMem(n/2, Vec(in, in))
        if (name != null) {
          println(s"Name support not implemented")
          //sram.setName(name)
        }
        val index_counter = Counter(en, n)._1
        val reg_waddr = Reg(next=(index_counter >> UInt(1)), init=UInt(n/2-1, log2Up(n)-1))
        val reg_raddr = Reg(UInt())
        val des = Reg(in.cloneType)
        val ser = Reg(in.cloneType)
        when (en) {
          when (index_counter(0)) {
            sram(reg_waddr) := Vec(des, in)
          }
            .otherwise {
              des := in
              reg_raddr := Mux(index_counter === UInt(n-2), UInt(0), (index_counter >> UInt(1)) + UInt(1))
            }
        }
        when (index_counter(0)) {
          out := ser
        }
          .otherwise {
            val sram_out = sram(reg_raddr)
            ser := sram_out(1)
            out := sram_out(0)
          }
        out
      }
    }
    else {
      val sram = SeqMem(n, in.cloneType)
      val index_counter = Counter(en, n)._1
      when (en) {
        sram(index_counter) := in
      }
      sram(index_counter)
    }
  }
}
