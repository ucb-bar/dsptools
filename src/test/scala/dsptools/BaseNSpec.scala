// SPDX-License-Identifier: Apache-2.0
/*
package dsptools

import chisel3._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

//scalastyle:off magic.number

class BaseNCircuit extends Module {
  val thing = Reg(BaseN(Seq.fill(5)(UInt(4.W)), rad = 3))
  val io = IO(new Bundle {
    val inc = Input(UInt(3.W))
    val out = Output(thing.cloneType)
  })
  val increment = BaseN(Seq.fill(5)(UInt(4.W)), rad = 3)

  increment := io.inc


  thing := thing + increment
  io.out := thing
}

class BaseNCircuitTester(c: BaseNCircuit) extends DspTester(c) {
  for(_ <- 0 to 7) {
    poke(c.io.inc, 1)
    step(1)
    println(f"current value ${peek(c.io.out.underlying.asUInt())}%04x") //scalastyle:off regex
  }
}

class BaseNSpec extends AnyFreeSpec with Matchers {
  "baseN tester increments" ignore {
    chisel3.iotesters.Driver(() => new BaseNCircuit) { c =>
      new BaseNCircuitTester(c)
    } should be(true)
  }

  "BaseN utilities have a number of capabilities" - {
    "create a list of ints from an int based on a radix" ignore {
      BaseN.toIntList(0, 5) should be (List(0))
      BaseN.toIntList(1, 5) should be (List(1))
      BaseN.toIntList(6, 5) should be (List(1, 1))
    }
    "create a compute length of list" ignore {
      BaseN.numDigits(0, 5) should be (1)
      BaseN.toIntList(1, 5) should be (1)
      BaseN.toIntList(6, 5) should be (2)
    }
  }
}
*/
