// SPDX-License-Identifier: Apache-2.0

/*
My first issue is :
How do I divide by 2 a DspComplex[T] ?

My input is two ADC with 8 bit each. The output is 8 bit DAC.
The output should be the sum of the inputs - amplitude does not matter as long as the hardware is utilized.

Is there a context in dsptools I can use, that will make summing two 8 bit numbers -> result in the sum not saturating?
For example in case of getting 255 in both inputs the result should be 510/2 = 255
Even better if I could use signed addition to do the same...
I thought about dividing the input by 2 and then summing - is this the way?
 */

package examples

import chisel3._
import dsptools._
import dsptools.numbers._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class ComplexDivider extends Module {
  val in = IO(Input(DspComplex(SInt(8.W), SInt(8.W))))
  val out = IO(Output(DspComplex(SInt(), SInt())))

  out.real := (in.real +& in.imag)
  out.imag := (in.real +& in.imag) / 2.S
}

class ComplexDivideBy2 extends AnyFreeSpec with Matchers {
  "divide by 2" in {
    dsptools.Driver.execute(
      () => new ComplexDivider,
      Array.empty[String]
    ) { c => new DspTester(c) {
        poke(c.in.real, 127.S)
        poke(c.in.imag, 127.S)
        println(s"got " + peek(c.out.real))
        println(s"got " + peek(c.out.imag))
        step(1)
        poke(c.in.real, -128.S)
        poke(c.in.imag, -128.S)
        println(s"got " + peek(c.out.real))
        println(s"got " + peek(c.out.imag))
      }
    } should be (true)
  }
}
