//// SPDX-License-Identifier: Apache-2.0
//
package examples

import chisel3._
import chiseltest._
import chiseltest.iotesters._
import org.scalatest.flatspec.AnyFlatSpec

//scalastyle:off magic.number

//case class CaseClassBundle(a: SInt) extends Bundle
//case class CaseClassBundle(underlying: SInt) extends Bundle {
//  val underlying = gen.cloneType
//  override def cloneType: this.type = new CaseClassBundle(underlying.cloneType).asInstanceOf[this.type]
//}
class CaseClassBundle(gen: SInt) extends Bundle {
  val underlying = gen
}

class SimpleCaseClassModule(gen: SInt) extends Module {
  val io = IO(new Bundle {
    val in = Input(new CaseClassBundle(gen))
    val out = Output(new CaseClassBundle(gen))
  })

  val register1 = Reg(io.out.cloneType)

  register1 := io.in

  io.out := register1
}

class SimpleCaseClassBundleTester(c: SimpleCaseClassModule) extends PeekPokeTester(c) {

  poke(c.io.in.underlying, 7)
  step(1)
  expect(c.io.out.underlying, 7)
}

class SimpleCaseClassBundleSpec extends AnyFlatSpec with ChiselScalatestTester {
  behavior.of("SimpleCaseClassBundle")

  it should "push number through with one step delay" in {
    test(new SimpleCaseClassModule(SInt(5.W)))
      .runPeekPoke(new SimpleCaseClassBundleTester(_))
  }
}
