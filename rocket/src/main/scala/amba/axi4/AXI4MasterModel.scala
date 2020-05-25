package freechips.rocketchip.amba.axi4

import chisel3._
import chisel3.iotesters.{PeekPokeTester, Pokeable}
import chisel3.util.IrrevocableIO
import dsptools.tester.MemMasterModel
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.util.BundleMap

object AXI4MasterModel {
  case class AWChannel(
                        id: BigInt                = 0,
                        addr: BigInt              = 0,
                        len: BigInt               = 0,
                        size: BigInt              = 0,
                        burst: BigInt             = 0,
                        lock: BigInt              = 0,
                        cache: BigInt             = 0,
                        prot: BigInt              = 0,
                        qos: BigInt               = 0,
                        region: BigInt            = 0,
                        user: Map[String, BigInt] = Map()
                      )
  case class ARChannel(
                        id: BigInt                = 0,
                        addr: BigInt              = 0,
                        len: BigInt               = 0,
                        size: BigInt              = 0,
                        burst: BigInt             = 0,
                        lock: BigInt              = 0,
                        cache: BigInt             = 0,
                        prot: BigInt              = 0,
                        qos: BigInt               = 0,
                        region: BigInt            = 0,
                        user: Map[String, BigInt] = Map()
                      )
  case class WChannel(
                       data: BigInt = 0,
                       strb: BigInt = 0,
                       last: BigInt = 0
                     )
  case class RChannel(
                       id: BigInt                = 0,
                       data: BigInt              = 0,
                       resp: BigInt              = 0,
                       last: BigInt              = 0,
                       user: Map[String, BigInt] = Map()
                     )
  case class BChannel(
                       id: BigInt                = 0,
                       resp: BigInt              = 0,
                       user: Map[String, BigInt] = Map()
                     )

  val BRESP_OKAY   = BigInt(0)
  val BRESP_EXOKAY = BigInt(1)
  val BRESP_SLVERR = BigInt(2)
  val BRESP_DECERR = BigInt(3)

  val RRESP_OKAY   = BigInt(0)
  val RRESP_EXOKAY = BigInt(1)
  val RRESP_SLVERR = BigInt(2)
  val RRESP_DECERR = BigInt(3)
}

trait AXI4MasterModel extends PeekPokeTester[MultiIOModule] with MemMasterModel {
  import AXI4MasterModel._

  def memAXI: AXI4Bundle

  def maxWait = 500

  def fire(io: IrrevocableIO[_]): Boolean = {
    (peek(io.valid) != BigInt(0)) && (peek(io.ready) != BigInt(0))
  }

  def pokeUser(user: BundleMap, values: Map[String, BigInt]): Unit = {
    for ( (k, v) <- values ) {
      user.elements.get(k) match {
        case Some(Pokeable(e)) =>
          poke(e, v)
        case Some(b: Bundle) =>
          poke(b, values)
        case Some(d) =>
          println(s"Don't know how to poke element $d")
        case None =>
          println(s"user field $k not found")
      }
    }
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
    pokeUser(aw.user, value.user)
  }


  def pokeAR(ar: AXI4BundleAR, value: ARChannel): Unit = {
    poke(ar.id, value.id)
    poke(ar.addr, value.addr)
    poke(ar.len, value.len)
    poke(ar.size, value.size)
    poke(ar.burst, value.burst)
    poke(ar.lock, value.lock)
    poke(ar.cache, value.cache)
    poke(ar.prot, value.prot)
    poke(ar.qos,  value.qos)
    pokeUser(ar.user, value.user)
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
      user = r.user.elements.map { case (n: String, Pokeable(d)) => n -> peek(d) }
    )
  }

  def peekB(b: AXI4BundleB): BChannel = {
    BChannel(
      id = peek(b.id),
      resp = peek(b.resp),
      user = b.user.elements.map { case (n: String, Pokeable(d)) => n -> peek(d) }
    )
  }

  def memWriteWord(addr: BigInt, data: BigInt): Unit = axiWriteWord(addr, data)
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

    var awFinished = false
    var wFinished = false
    var cyclesWaited = 0

    poke(memAXI.aw.valid, 1)
    poke(memAXI.w.valid,  1)

    while (!awFinished || !wFinished) {
      if (!awFinished) { awFinished = fire(memAXI.aw) }
      if (! wFinished) {  wFinished = fire(memAXI.w)  }
      require(cyclesWaited < maxWait || awFinished, s"Timeout waiting for AW to be ready ($maxWait cycles)")
      require(cyclesWaited < maxWait || wFinished,  s"Timeout waiting for W to be ready ($maxWait cycles)")
      // if (cyclesWaited >= maxWait) {
      //   return
      // }
      cyclesWaited += 1
      step(1)
      if (awFinished) { poke(memAXI.aw.valid, 0) }
      if ( wFinished) { poke(memAXI.w.valid,  0) }
    }

    // wait for resp
    cyclesWaited = 0
    poke(memAXI.b.ready, 1)
    var bFinished = false
    var b = peekB(memAXI.b.bits)

    while (!bFinished) {
      bFinished = peek(memAXI.b.valid) != BigInt(0)
      b = peekB(memAXI.b.bits)
      require(cyclesWaited < maxWait, s"Timeout waiting for B to be valid ($maxWait cycles)")
      step(1)
      cyclesWaited += 1
    }

    poke(memAXI.b.ready, 0)

    require(b.id == awChannel.id, s"Got bad id (${b.id} != ${awChannel.id})")
    require(b.resp == BRESP_OKAY, s"BRESP not OKAY (got ${b.resp}")

  }
  def memReadWord(addr: BigInt) = axiReadWord(addr)
  def axiReadWord(addr: BigInt): BigInt = {
    val arChannel = ARChannel(
      addr = addr,
      size = 3        // 8 bytes
    )

    pokeAR(memAXI.ar.bits, arChannel)
    poke(memAXI.ar.valid, 1)
    
    var cyclesWaited = 0
    var arFinished = false

    while (!arFinished) {
      arFinished = peek(memAXI.ar.ready) != BigInt(0)
      require(cyclesWaited < maxWait, s"Timeout waiting for AR to be ready ($maxWait cycles)")
      /* if (cyclesWaited >= maxWait) {
        println(s"Timeout waiting for AR to be ready ($maxWait cycles)")
        arFinished = true
      } */
      step(1)
      cyclesWaited += 1
    }

    poke(memAXI.ar.valid, 0)
    poke(memAXI.r.ready, 1)
    //step(1)

    var rFinished = false
    cyclesWaited = 0
    var rChannel = peekR(memAXI.r.bits)

    while (!rFinished) {
      poke(memAXI.ar.valid, 0)
      rFinished = peek(memAXI.r.valid) != BigInt(0)
      if (rFinished) {
        rChannel = peekR(memAXI.r.bits)
      }
      step(1)
      require(cyclesWaited < maxWait, s"Timeout waiting for R to be valid ($maxWait cycles)")
      /* if (cyclesWaited >= maxWait) {
        println(s"Timeout waiting for R to be ready ($maxWait cycles)")
        rFinished = true // hack hack hack
      } */

      cyclesWaited += 1
    }

    poke(memAXI.r.ready, 0)

    require(rChannel.last != BigInt(0))
    require(rChannel.id == arChannel.id, s"Got id ${rChannel.id} instead of ${arChannel.id}")
    require(rChannel.resp == RRESP_OKAY, s"RRESP not OKAY (got ${rChannel.resp}")
    rChannel.data
  }

  def axiReset(): Unit = {
    pokeAR(memAXI.ar.bits, ARChannel())
    pokeAW(memAXI.aw.bits, AWChannel())
    pokeW(memAXI.w.bits, WChannel())
    poke(memAXI.ar.valid, 0)
    poke(memAXI.aw.valid, 0)
    poke(memAXI.w.valid, 0)
    poke(memAXI.r.ready, 0)
    poke(memAXI.b.ready, 0)
  }
}
