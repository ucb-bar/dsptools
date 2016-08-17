//// See LICENSE for license details.
//
package examples

import chisel3.core._
import chisel3.iotesters.PeekPokeTester
import chisel3.{Bundle, Module}
import org.scalatest.{FlatSpec, Matchers}


//case class CaseClassBundle(a: SInt) extends Bundle
//case class CaseClassBundle(underlying: SInt) extends Bundle {
  //  val underlying = gen.cloneType
  //  override def cloneType: this.type = new CaseClassBundle(underlying.cloneType).asInstanceOf[this.type]
//}
class CaseClassBundle(gen: SInt) extends Bundle {
    val underlying = gen
//    override def cloneType: this.type = new CaseClassBundle(underlying.cloneType).asInstanceOf[this.type]
}

class SimpleCaseClassModule(gen: SInt) extends Module {
  val io = new Bundle {
    val in = (new CaseClassBundle(gen)).flip()
    val out  = new CaseClassBundle(gen)
  }

  val register1 = Reg(io.out)

  register1 := io.in

  io.out := register1
}

class SimpleCaseClassBundleTester(c: SimpleCaseClassModule) extends PeekPokeTester(c) {

  poke(c.io.in.underlying, 7)
  step(1)
  println(s"SimpleCaseClassBundle: pushed 7 got ${peek(c.io.out.underlying)}")

}

class SimpleCaseClassBundleSpec extends FlatSpec with Matchers {
  behavior of "SimpleCaseClassBundle"

  it should "push number through with one step delay" in {
    chisel3.iotesters.Driver(() => new SimpleCaseClassModule(SInt(width = 5))) { c =>
      new SimpleCaseClassBundleTester(c)
    } should be(true)

  }
}
