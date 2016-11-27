//// See LICENSE for license details.
//
package examples

import chisel3._
import chisel3.iotesters.PeekPokeTester
import org.scalatest.{FlatSpec, Matchers}

//scalastyle:off magic.number

//case class CaseClassBundle(a: SInt) extends Bundle
//case class CaseClassBundle(underlying: SInt) extends Bundle {
  //  val underlying = gen.cloneType
  //  override def cloneType: this.type = new CaseClassBundle(underlying.cloneType).asInstanceOf[this.type]
//}
class CaseClassBundle(gen: SInt) extends Bundle {
    val underlying = gen
    override def cloneType: this.type = new CaseClassBundle(underlying.cloneType).asInstanceOf[this.type]
}

class SimpleCaseClassModule(gen: SInt) extends Module {
  val io = IO(new Bundle {
    val in  = Input(new CaseClassBundle(gen))
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

class SimpleCaseClassBundleSpec extends FlatSpec with Matchers {
  behavior of "SimpleCaseClassBundle"

  it should "push number through with one step delay" in {
    chisel3.iotesters.Driver(() => new SimpleCaseClassModule(SInt(5.W))) { c =>
      new SimpleCaseClassBundleTester(c)
    } should be(true)

  }
}
