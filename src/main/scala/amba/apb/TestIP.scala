// See LICENSE for license details

package amba.apb

import chisel3.experimental.BaseModule
import freechips.rocketchip.amba.apb._

trait APBMasterModel[T <: BaseModule] { this: chisel3.iotesters.PeekPokeTester[T] =>
  def memAPB: APBBundle

  def apbReset(): Unit = {

  }

  def apbWrite(addr: Int, data: Int): Unit =
    apbWrite(BigInt(addr), BigInt(data))

  def apbWrite(addr: BigInt, data: BigInt): Unit = {
    var count = 0
    poke(memAPB.psel, 1)
    poke(memAPB.penable, 0)
    poke(memAPB.pwrite, 1)
    poke(memAPB.pwdata, data)
    poke(memAPB.paddr, addr)
    poke(memAPB.pstrb, 0xff)
    step(1)
    poke(memAPB.psel, 1)
    poke(memAPB.penable, 1)
    step(1)
    while (peek(memAPB.pready) == 0 && count < 1000) {
      step(1)
      count += 1
    }
    poke(memAPB.psel, 0)
    poke(memAPB.penable, 0)
  }

  def apbRead(addr: Int): BigInt =
    apbRead(BigInt(addr))

  def apbRead(addr: BigInt): BigInt = {
    poke(memAPB.psel, 1)
    poke(memAPB.penable, 0)
    poke(memAPB.pwrite, 0)
    poke(memAPB.paddr, addr)
    step(1)
    poke(memAPB.penable, 1)
    while (peek(memAPB.pready) == 0) {
      step(1)
    }
    poke(memAPB.psel, 0)
    poke(memAPB.penable, 0)
    peek(memAPB.prdata)
  }
}

