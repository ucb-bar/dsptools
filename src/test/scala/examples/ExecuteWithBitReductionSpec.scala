// See LICENSE for license details.

package examples

import chisel3._
import chisel3.experimental.FixedPoint
import dsptools.DspTester
import dsptools.numbers._
import org.scalatest.{FreeSpec, Matchers}

//scalastyle:off magic.number

class TooBigWireModule extends Module {
  val io = IO(new Bundle {
    val a1    = Input(SInt(8.W))
    val a2    = Input(SInt(8.W))
    val c     = Output(SInt(8.W))
  })

  val register1 = Reg(SInt(8.W))

  register1 := io.a1 + io.a2

  io.c := register1
}

class TooBigWireModuleTester(c: TooBigWireModule) extends DspTester(c) {
  for {
    i <- -2 to 1
    j <- -2 to 1
  } {
    poke(c.io.a1, i)
    poke(c.io.a2, j)
    step(1)

    val result = peek(c.io.c)

    println(s"parameterize adder tester $i + $j => $result")
    expect(c.io.c, i + j, s"parameterize adder tester $i + $j => $result should have been ${i + j}")
  }
}

class TooBigWireModuleSpec extends FreeSpec with Matchers {

  "reduce bits on SInt register" in {
    dsptools.Driver.executeWithBitReduction(() => new TooBigWireModule,
      Array("-fimhb", "16", "-dtinv")) { c =>
      new TooBigWireModuleTester(c)
    } should be (true)
  }
}


class UIntTooBigWireModule extends Module {
  val io = IO(new Bundle {
    val a1    = Input(UInt(8.W))
    val a2    = Input(UInt(8.W))
    val c     = Output(UInt(8.W))
  })

  val register1 = Reg(UInt(8.W))

  register1 := io.a1 + io.a2

  io.c := register1
}

class UIntTooBigWireModuleTester(c: UIntTooBigWireModule) extends DspTester(c) {
  for {
    i <- 0 to 2
    j <- 0 to 2
  } {
    poke(c.io.a1, i)
    poke(c.io.a2, j)
    step(1)

    val result = peek(c.io.c)

    println(s"parameterize adder tester $i + $j => $result")
    expect(c.io.c, i + j, s"parameterize adder tester $i + $j => $result should have been ${i + j}")
  }
}

class UIntTooBigWireModuleSpec extends FreeSpec with Matchers {

  "reduce bits on UInt register" in {
    dsptools.Driver.executeWithBitReduction(() => new UIntTooBigWireModule,
      Array("-fimhb", "16", "-dtinv")) { c =>
      new UIntTooBigWireModuleTester(c)
    } should be (true)
  }
}

object AddModuleIO {
  val TestWidth = 10.W
}

class AddModuleIO extends Bundle {
  val a1    = Input(UInt(AddModuleIO.TestWidth))
  val a2    = Input(UInt(AddModuleIO.TestWidth))
  val c     = Output(UInt(AddModuleIO.TestWidth))
}

class BitReduceSubModule extends Module {
  val io = IO(new AddModuleIO)
  val register1 = Reg(UInt(AddModuleIO.TestWidth))

  register1 := io.a1 + io.a2

  io.c := register1
}

class BitReduceParentModule extends Module {
  val io = IO(new Bundle {
    val bank0    = new AddModuleIO
    val bank1    = new AddModuleIO
  })

  val mod2 = Module(new BitReduceSubModule)
  val mod1 = Module(new BitReduceSubModule)

  mod2.io.a1 := io.bank0.a1
  mod2.io.a2 := io.bank0.a2
  io.bank0.c := mod2.io.c

  mod1.io.a1 := io.bank1.a1
  mod1.io.a2 := io.bank1.a2
  io.bank1.c := mod1.io.c
}

class BitReduceSubWithModuleTester(c: BitReduceParentModule) extends DspTester(c) {
  for {
    i <- 0 to 8
    j <- 0 to 8
  } {
    poke(c.io.bank1.a1, i)
    poke(c.io.bank1.a2, j)
    step(1)

    val result = peek(c.io.bank1.c)

    println(s"Bank1 tests adder tester $i + $j => $result")
    expect(c.io.bank1.c, i + j, s"parameterize adder tester $i + $j => $result should have been ${i + j}")
  }

  for {
    i <- 0 to 2
    j <- 0 to 2
  } {
    poke(c.io.bank0.a1, i)
    poke(c.io.bank0.a2, j)
    step(1)

    val result = peek(c.io.bank0.c)

    println(s"Bank0 tests adder tester $i + $j => $result")
    expect(c.io.bank0.c, i + j, s"parameterize adder tester $i + $j => $result should have been ${i + j}")
  }

}

class BitReduceSubWithModuleSpec extends FreeSpec with Matchers {

  "Run with two submodules who see different values" in {
    dsptools.Driver.executeWithBitReduction(() => new BitReduceParentModule,
      Array("-fimhb", "16", "-dtinv")) { c =>
      new BitReduceSubWithModuleTester(c)
    } should be (true)
  }
}
