// See LICENSE for license details

package freechips.rocketchip.tilelink

import chisel3.MultiIOModule
import dsptools.tester.MemMasterModel

object TLMasterModel {
  case class AChannel(
    opcode:  BigInt = 0, // PutFullData
    param:   BigInt = 0, // toT
    size:    BigInt = 2,
    source:  BigInt = 1,
    address: BigInt = 0,
    mask:    BigInt = 0xff,
    data:    BigInt = 0)

  case class BChannel(
    opcode:  BigInt = 0,
    param:   BigInt = 0,
    size:    BigInt = 0,
    source:  BigInt = 0,
    address: BigInt = 0,
    mask:    BigInt = 0,
    data:    BigInt = 0)

  case class CChannel(
    opcode:  BigInt = 0,
    param:   BigInt = 0,
    size:    BigInt = 0,
    source:  BigInt = 0,
    address: BigInt = 0,
    data:    BigInt = 0,
    corrupt:   Boolean = false)

  case class DChannel(
    opcode:  BigInt = 0,
    param:   BigInt = 0,
    size:    BigInt = 0,
    source:  BigInt = 0,
    sink:    BigInt = 0,
    data:    BigInt = 0,
    corrupt:   Boolean = false)

  case class EChannel(
    sink: BigInt = 0)
}

//noinspection RedundantDefaultArgument
trait TLMasterModel extends chisel3.iotesters.PeekPokeTester[MultiIOModule] with MemMasterModel {
  import TLMasterModel._

  def memTL: TLBundle

  def tlReset(): Unit = {
    pokeA(AChannel())
    pokeC(CChannel())
    pokeE(EChannel())
    poke(memTL.a.valid, 0)
    poke(memTL.b.ready, 0)
    poke(memTL.c.valid, 0)
    poke(memTL.d.ready, 0)
    poke(memTL.e.valid, 0)
  }

  def pokeA(a: AChannel): Unit = {
    poke(memTL.a.bits.opcode,  a.opcode)
    poke(memTL.a.bits.param,   a.param)
    poke(memTL.a.bits.size,    a.size)
    poke(memTL.a.bits.source,  a.source)
    poke(memTL.a.bits.address, a.address)
    poke(memTL.a.bits.mask,    a.mask)
    poke(memTL.a.bits.data,    a.data)
  }

  def tlWriteA(a: AChannel): Unit = {
    poke(memTL.a.valid, 1)
    pokeA(a)

    while(peek(memTL.a.ready) != BigInt(0)) {
      step(1)
    }
    step(1)
    poke(memTL.a.valid, 0)
  }

  def peekB(): BChannel = {

    val opcode  = peek(memTL.b.bits.opcode)
    val param   = peek(memTL.b.bits.param)
    val size    = peek(memTL.b.bits.size)
    val source  = peek(memTL.b.bits.source)
    val address = peek(memTL.b.bits.address)
    val mask    = peek(memTL.b.bits.mask)
    val data    = peek(memTL.b.bits.data)

    BChannel(
      opcode=opcode,
      param=param,
      size=size,
      source=source,
      address=address,
      mask=mask,
      data=data)
  }

  def tlReadB(): BChannel = {
    poke(memTL.b.ready, 1)

    while (peek(memTL.b.valid) != BigInt(0)) {
      step(1)
    }

    step(1)

    poke(memTL.b.ready, 0)

    peekB()
  }

  def pokeC(c: CChannel): Unit = {
    poke(memTL.c.bits.opcode,  c.opcode)
    poke(memTL.c.bits.param,   c.param)
    poke(memTL.c.bits.size,    c.size)
    poke(memTL.c.bits.source,  c.source)
    poke(memTL.c.bits.address, c.address)
    poke(memTL.c.bits.data,    c.data)
    poke(memTL.c.bits.corrupt, c.corrupt)

  }

  def tlWriteC(c: CChannel): Unit = {
    poke(memTL.c.valid, 1)
    pokeC(c)

    while(peek(memTL.c.ready) != BigInt(0)) {
      step(1)
    }
    step(1)
    poke(memTL.c.valid, 0)
  }

  def peekD(): DChannel = {
    val opcode  = peek(memTL.d.bits.opcode)
    val param   = peek(memTL.d.bits.param)
    val size    = peek(memTL.d.bits.size)
    val source  = peek(memTL.d.bits.source)
    val sink    = peek(memTL.d.bits.sink)
    val data    = peek(memTL.d.bits.data)
    val corrupt = peek(memTL.d.bits.corrupt)

    DChannel(
      opcode=opcode,
      param=param,
      size=size,
      source=source,
      sink=sink,
      data=data,
      corrupt = corrupt != BigInt(0))
  }

  def tlReadD(): DChannel = {
    poke(memTL.d.ready, 1)

    while (peek(memTL.d.valid) != BigInt(0)) {
      step(1)
    }
    val d = peekD()
    step(1)

    poke(memTL.d.ready, 0)
    d
  }

  def pokeE(e: EChannel): Unit = {
    poke(memTL.e.bits.sink,   e.sink)
  }

  def tlWriteE(e: EChannel): Unit = {
    poke(memTL.e.valid, 1)
    pokeE(e)

    while(peek(memTL.e.ready) != BigInt(0)) {
      step(1)
    }
    step(1)
    poke(memTL.e.valid, 0)
  }

  def memWriteWord(addr: BigInt, data: BigInt): Unit = tlWriteWord(addr, data)
  def tlWriteWord(addr: BigInt, data: BigInt): Unit = {
    tlWriteA(AChannel(opcode = 0 /* PUT */, address=addr, data=data, mask = BigInt("1"*8, 2)))
    tlReadD()
  }

  def tlWriteByte(addr: BigInt, data: Int): Unit = {
    tlWriteA(AChannel(opcode = 0 /* PUT */, address = addr, data=data, mask = BigInt("1"*8, 2)))
    tlReadD()
  }

  def tlWriteBytes(addr: BigInt, data: Seq[Int]): Unit = {
    data.zipWithIndex.foreach { case (d, i) =>
      tlWriteByte(addr + i, d)
    }
  }

  def memReadWord(addr: BigInt): BigInt = tlReadWord(addr)
  def tlReadWord(addr: BigInt): BigInt = {
    tlWriteA(AChannel(opcode = 4 /* GET */, address=addr))
    val d = tlReadD()
    d.data
  }

}
