package dsptools

import chisel3.iotesters.{PeekPokeTester, TesterOptionsManager}
import org.scalatest.{FlatSpec, Matchers}

class SyncROMBlackBoxTester(c: SyncROM) extends PeekPokeTester(c) {
  val max = BigInt(1) << c.addrWidth
  def getValueAtIdx(idx: BigInt): BigInt = {
    if (idx < c.table.length) {
      c.table(idx.toInt)
    } else {
      BigInt(0)
    }
  }

  // forwards
  var cnt = BigInt(0)

  while (cnt < max) {
    poke(c.io.addr, cnt)
    step(1)
    expect(c.io.data, getValueAtIdx(cnt))

    cnt += 1
  }

  // backwards
  cnt = max - 1
  while (cnt >= 0) {
    poke(c.io.addr, cnt)
    step(1)
    expect(c.io.data, getValueAtIdx(cnt))

    cnt -= 1
  }
}

class SyncROMSpec extends FlatSpec with Matchers {
  behavior of "SyncROM"

  val testTable:Seq[BigInt] = (0 until 2049).map(BigInt(_))

  it should "work with verilator" in {
    chisel3.iotesters.Driver.execute(Array("-tbn", "verilator"),
      () => new SyncROM("verilatorROM", testTable)) {
      c => new SyncROMBlackBoxTester(c)
    } should be (true)
  }

  it should "work with firrtl interpreter" in {
    val options = new TesterOptionsManager {
      interpreterOptions = interpreterOptions.copy(
        blackBoxFactories = interpreterOptions.blackBoxFactories :+ new SyncROMBlackBoxFactory
      )
    }
    chisel3.iotesters.Driver.execute(
      () => new SyncROM("firrtlROM", testTable),
      options) {
      c => new SyncROMBlackBoxTester(c)
    } should be (true)
  }
}
