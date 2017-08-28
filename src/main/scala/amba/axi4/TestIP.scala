package amba.axi4

import chisel3.Module
import chisel3.util._
import freechips.rocketchip.amba.axi4._

object AXI4MasterModel {
  case class AWChannel(
                        id: BigInt     = 0,
                        addr: BigInt   = 0,
                        len: BigInt    = 0,
                        size: BigInt   = 0,
                        burst: BigInt  = 0,
                        lock: BigInt   = 0,
                        cache: BigInt  = 0,
                        prot: BigInt   = 0,
                        qos: BigInt    = 0,
                        region: BigInt = 0,
                        user: BigInt   = 0
                      )
  case class ARChannel(
                        id: BigInt     = 0,
                        addr: BigInt   = 0,
                        len: BigInt    = 0,
                        size: BigInt   = 0,
                        burst: BigInt  = 0,
                        lock: BigInt   = 0,
                        cache: BigInt  = 0,
                        prot: BigInt   = 0,
                        qos: BigInt    = 0,
                        region: BigInt = 0,
                        user: BigInt   = 0
                      )
  case class WChannel(
                       data: BigInt = 0,
                       strb: BigInt = 0,
                       last: BigInt = 0
                     )
  case class RChannel(
                       id: BigInt   = 0,
                       data: BigInt = 0,
                       resp: BigInt = 0,
                       last: BigInt = 0,
                       user: BigInt = 0
                     )
  case class BChannel(
                       id: BigInt   = 0,
                       resp: BigInt = 0,
                       user: BigInt = 0
                     )

  val BRESP_OKAY   = BigInt(0)
  val BRESP_EXOKAY = BigInt(1)
  val BRESP_SLVERR = BigInt(2)
  val BRESP_DECERR = BigInt(3)
}

trait AXI4MasterModel[T <: Module] { this: chisel3.iotesters.PeekPokeTester[T] =>
  import AXI4MasterModel._

  def memAXI: AXI4Bundle

  val maxWait = 100

  def fire(io: IrrevocableIO[_]): Boolean = {
    return (peek(io.valid) != BigInt(0)) && (peek(io.ready) != BigInt(0))
  }

  def pokeAW(aw: AXI4BundleAW, value: AWChannel): Unit = {
    poke(aw.id,     value.id)
    poke(aw.addr,   value.addr)
    poke(aw.len,    value.len)
    poke(aw.size,   value.size)
    poke(aw.burst,  value.burst)
    poke(aw.lock,   value.lock)
    poke(aw.cache,  value.cache)
    poke(aw.prot,   value.prot)
    poke(aw.qos,    value.qos)
    // poke(aw., value.region)
    require(value.region == BigInt(0), s"region is optional and rocket-chip left it out. overriding the default value here with ${value.region} won't do anything")
    aw.user.map { u => poke(u,   value.user) } getOrElse(
      if (value.user == 0) {
        println("user is optional and in this instance it was left out. overriding the default value here won't do anything")
      }
    )
  }


  def pokeAR(ar: AXI4BundleAR, value: ARChannel): Unit = {

  }

  def pokeW(w: AXI4BundleW, value: WChannel): Unit = {
    poke(w.data, value.data)
    poke(w.strb, value.strb)
    poke(w.last, value.last)
  }

  def peekR(r: AXI4BundleR): RChannel = {
    RChannel(
      id   = peek(r.id),
      data = peek(r.data),
      resp = peek(r.resp),
      last = peek(r.last),
      user = r.user.map { peek(_) } getOrElse(0)
    )
  }

  def peekB(b: AXI4BundleB): BChannel = {
    BChannel(
      id = peek(b.id),
      resp = peek(b.resp),
      user = b.user.map { peek(_) } getOrElse(0)
    )
  }

  def axiWriteWord(addr: BigInt, data: BigInt): Unit = {
    val awChannel = AWChannel(
      addr = addr,
      size = 3        // 8 bytes
    )
    val wChannel  = WChannel(
      data = data,
      strb = 0xFF,    // 8 bytes
      last = true    // one word only
    )

    // poke AW and W channels
    pokeAW(memAXI.aw.bits, awChannel)
    pokeW(memAXI.w.bits, wChannel)

    var aw_finished = false
    var w_finished = false
    var cyclesWaited = 0

    poke(memAXI.aw.valid, 1)
    poke(memAXI.w.valid,  1)

    while (!aw_finished && !w_finished) {
      if (!aw_finished) { aw_finished = fire(memAXI.aw) }
      if (!w_finished)  { w_finished  = fire(memAXI.w)  }
      require(cyclesWaited < maxWait, s"Timeout waiting for AW or W to be ready ($maxWait cycles)")
      cyclesWaited += 1
      step(1)
      if (aw_finished) { poke(memAXI.aw.valid, 0) }
      if ( w_finished) { poke(memAXI.w.valid,  0) }
    }

    // wait for resp
    cyclesWaited = 0
    poke(memAXI.b.ready, 1)
    var b_finished = false
    while (!b_finished) {
      b_finished = peek(memAXI.b.valid) != BigInt(0)
      require(cyclesWaited < maxWait, s"Timeout waiting for B to be ready ($maxWait cycles)")
      step(1)
      cyclesWaited += 1
    }

    poke(memAXI.b.ready, 0)

    val b = peekB(memAXI.b.bits)
    require(b.id == awChannel.id)
    require(b.resp == BRESP_OKAY, s"BRESP not OKAY (geto ${b.resp}")

  }
  def axiReadWord(addr: BigInt): BigInt = {
    BigInt(0) // TODO
  }
}