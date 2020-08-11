// See LICENSE for license details.

package generatortools.io

import chisel3._
import chisel3.experimental.{ChiselRange, Interval}
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}
import firrtl.stage.FirrtlSourceAnnotation
import org.scalatest.{FlatSpec, Matchers}

/** Makes modules with Intervals, custom clocks, etc. compatible with the old testing strategy.
  * dutFactory = DUT module
  * useGlobalClk/useGlobalRst = use implicit clock and reset
  */
class TestModule[T <: Module](val dutFactory: () => T,
                              useGlobalClk:   Boolean = true,
                              useGlobalRst:   Boolean = true,
                              name:           String = "")
    extends Module {

  /** Generate standard Chisel top-level IO */
  private def createTopIO[R <: Record](intIO: R) = {
    // Internally clones type
    // new CustomBundle(intIO.elements.toList.map { case (field, elt) => field -> ConvertType(elt) }: _*)
    intIO.cloneType
  }

  // Wrap + connect DUT
  val dut = Module(dutFactory())
  val io = IO(createTopIO(dut.io))

  if (name.nonEmpty) dut.suggestName(name)

  // Make sure global clock/reset functionality doesn't accidentally get mixed in
  if (useGlobalClk) dut.clock := clock
  else dut.clock := false.B.asClock
  if (useGlobalRst) dut.reset := reset
  else dut.reset := true.B

  Connect(io, dut.io, if (useGlobalClk) Some(clock) else None)

  // Easy access to top-level IO + DUT IO (by name)
  def getIO(str:    String) = io.elements(str)
  def getDutIO(str: String) = dut.io.elements(str)

}

class FakeDUTIO extends Bundle {
  val a = Interval(range"[-3.3, 2.2].5")
  val b = Interval(range"[4, 8]")
  val c = Interval(range"[-10, 8]")
  val clk = Clock()
}

class FakeDUT extends Module {
  val io = IO(new Bundle {
    val i = Input(new FakeDUTIO)
    val o = Output(new FakeDUTIO)
  })
  io.o <> io.i
}

class TestModuleSpec extends FlatSpec with Matchers {
  behavior.of("TestModule")
  it should "generate correct FIRRTL without global clock" in {
    val firrtl = (new ChiselStage).emitFirrtl(new TestModule(() => new FakeDUT, useGlobalClk = false))

    val expectedFirrtlT =
      """circuit TestModule :
        |  module FakeDUT :
        |    input clock : Clock
        |    input reset : UInt<1>
        |    output io : { flip i : { a : Interval[-3.28125, 2.1875].5, b : Interval[4, 8].0, c : Interval[-10, 8].0, clk : Clock}, o : { a : Interval[-3.28125, 2.1875].5, b : Interval[4, 8].0, c : Interval[-10, 8].0, clk : Clock}}
        |
        |    io.o.clk <= io.i.clk @[TestModule.scala 61:8]
        |    io.o.c <= io.i.c @[TestModule.scala 61:8]
        |    io.o.b <= io.i.b @[TestModule.scala 61:8]
        |    io.o.a <= io.i.a @[TestModule.scala 61:8]
        |
        |  module TestModule :
        |    input clock : Clock
        |    input reset : UInt<1>
        |    output io : { flip i : { a : Interval[-3.28125, 2.1875].5, b : Interval[4, 8].0, c : Interval[-10, 8].0, clk : Clock}, o : { a : Interval[-3.28125, 2.1875].5, b : Interval[4, 8].0, c : Interval[-10, 8].0, clk : Clock}}
        |
        |    inst dut of FakeDUT @[TestModule.scala 30:19]
        |    dut.clock <= clock
        |    dut.reset <= reset
        |    node _T = asClock(UInt<1>("h0")) @[TestModule.scala 37:29]
        |    dut.clock <= _T @[TestModule.scala 37:18]
        |    dut.reset <= reset @[TestModule.scala 38:31]
        |    io.o.clk <= dut.io.o.clk @[Utils.scala 153:14]
        |    io.o.c <= dut.io.o.c @[Utils.scala 153:14]
        |    io.o.b <= dut.io.o.b @[Utils.scala 153:14]
        |    io.o.a <= dut.io.o.a @[Utils.scala 153:14]
        |    dut.io.i.clk <= io.i.clk @[Utils.scala 153:14]
        |    dut.io.i.c <= io.i.c @[Utils.scala 153:14]
        |    dut.io.i.b <= io.i.b @[Utils.scala 153:14]
        |    dut.io.i.a <= io.i.a @[Utils.scala 153:14]
        |    """.stripMargin

    val expectedFirrtl = expectedFirrtlT.replaceAll(" ", "").split("\n").map(_.split("@").head).mkString("\n")
    val newFirrtl = firrtl.replaceAll(" ", "").split("\n").map(_.split("@").head).mkString("\n")
    println(s"Generated Firrtl\n${"=" * 100}\n$firrtl\nExpected Firrtl\n${"=" * 100}\n$expectedFirrtlT")
    require(newFirrtl.contains(expectedFirrtl))
  }
}
