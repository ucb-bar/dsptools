// SPDX-License-Identifier: Apache-2.0

package testing.dsptools.numbers

import chisel3._
import chisel3.iotesters.ChiselPropSpec
import chisel3.testers.BasicTester
import dsptools.numbers._

//scalastyle:off magic.number
class DspComplexExamples extends Module {
  val io = IO(new Bundle {
    val in = Input(DspComplex(SInt(5.W), SInt(5.W)))
    val outJ = Output(DspComplex(SInt(5.W), SInt(5.W)))
    val inByJ = Output(DspComplex(SInt(5.W), SInt(5.W)))
    val inByJShortcut = Output(DspComplex(SInt(5.W), SInt(5.W)))
  })

  io.outJ := DspComplex.j[SInt]
  io.inByJ := io.in * DspComplex.j[SInt]
  io.inByJShortcut := io.in.mulj()
}

class DspComplexExamplesTester extends BasicTester {
  val dut = Module(new DspComplexExamples)

  dut.io.in.real      := 7.S
  dut.io.in.imag := (-4).S

  printf(s"inByJ.real: %d\n", dut.io.inByJ.real)
  printf(s"inByJ.imag: %d\n", dut.io.inByJ.imag)

  printf(s"inByJShortcut.real: %d\n", dut.io.inByJShortcut.real)
  printf(s"inByJShortcut.imag: %d\n", dut.io.inByJShortcut.imag)

  assert(dut.io.outJ.real === 0.S)
  assert(dut.io.outJ.imag === 1.S)

  assert(dut.io.inByJ.real === 4.S)
  assert(dut.io.inByJ.imag === 7.S)

  assert(dut.io.inByJShortcut.real === 4.S)
  assert(dut.io.inByJShortcut.imag === 7.S)

  stop()
}

class SIntTester extends BasicTester {
  val x = 10.S

  val xcopy = Wire(x.cloneType)
  xcopy := x

  assert( x === xcopy )

  val y = DspComplex((-4).S, (-1).S)

  assert ( y.real === (-4).S)
  assert (y.imag === (-1).S)

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
