// See LICENSE for license details.

package dsptools.numbers

import chisel3._
import chisel3.iotesters.ChiselPropSpec
import chisel3.testers.BasicTester
import dsptools.numbers.implicits._
import chisel3.{Bundle, Module, SInt, printf}

class DspComplexExamples extends Module {
  val io = IO(new Bundle {
    val in = Input(DspComplex(SInt(width=5), SInt(width=5)))
    val outJ = Output(DspComplex(SInt(width=5), SInt(width=5)))
    val inByJ = Output(DspComplex(SInt(width=5), SInt(width=5)))
    val inByJShortcut = Output(DspComplex(SInt(width=5), SInt(width=5)))
  })

  io.outJ := DspComplex.j[SInt]
  io.inByJ := io.in * DspComplex.j[SInt]
  io.inByJShortcut := DspComplex.multiplyByJ(io.in)
}

class DspComplexExamplesTester extends BasicTester {
  val dut = Module(new DspComplexExamples)

  dut.io.in.real      := SInt(7)
  dut.io.in.imaginary := SInt(-4)

  printf(s"inByJ.real: %d\n", dut.io.inByJ.real)
  printf(s"inByJ.imaginary: %d\n", dut.io.inByJ.imaginary)

  printf(s"inByJShortcut.real: %d\n", dut.io.inByJShortcut.real)
  printf(s"inByJShortcut.imaginary: %d\n", dut.io.inByJShortcut.imaginary)

  assert(dut.io.outJ.real === SInt(0))
  assert(dut.io.outJ.imaginary === SInt(1))

  assert(dut.io.inByJ.real === SInt(4))
  assert(dut.io.inByJ.imaginary === SInt(7))

  assert(dut.io.inByJShortcut.real === SInt(4))
  assert(dut.io.inByJShortcut.imaginary === SInt(7))

  stop()
}

class SIntTester extends BasicTester {
  val x = SInt(10)

  val xcopy = Wire(x.cloneType)
  xcopy := x

  assert( x === xcopy )

  val y = DspComplex.wire(SInt(-4), SInt(-1))

  assert ( y.real === (-4).S)
  assert (y.imaginary === (-1).S)

  stop()
}

class DspComplexExamplesSpec extends ChiselPropSpec {
  property("using j with complex numbers should work") {
    assertTesterPasses { new DspComplexExamplesTester}
  }

  property("assigning Wire(SInt) should work") {
    assertTesterPasses { new SIntTester }
  }
}
