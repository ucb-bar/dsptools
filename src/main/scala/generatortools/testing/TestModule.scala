package generatortools.testing
import chisel3._
import chisel3.experimental.{Interval, ChiselRange}
import chisel3.internal.firrtl.IntervalRange
import generatortools.io.{CustomBundle, ConvertType, Connect}
import org.scalatest.{Matchers, FlatSpec}

/** Makes modules with Intervals, custom clocks, etc. compatible with the old testing strategy.
  * dutFactory = DUT module
  * useGlobalClk/useGlobalRst = use implicit clock and reset
  */
class TestModule[T <: Module](val dutFactory: () => T, useGlobalClk: Boolean = true, useGlobalRst: Boolean = true)
    extends Module {

  /** Generate standard Chisel top-level IO */
  private def createTopIO[T <: Record](intIO: T): CustomBundle[Data] =
    // Internally clones type
    new CustomBundle(intIO.elements.toList.map { case (field, elt) => field -> ConvertType(elt) }: _*)

  // Wrap + connect DUT
  val dut = Module(dutFactory())
  val io = IO(createTopIO(dut.io))

  // Make sure global clock/reset functionality doesn't accidentally get mixed in
  if (useGlobalClk) dut.clock := clock
  else dut.clock := false.B.asClock
  if (useGlobalRst) dut.reset := reset
  else dut.reset := true.B

  Connect(io, dut.io, if (useGlobalClk) Some(clock) else None)

  // Easy access to top-level IO + DUT IO (by name)
  def getIO(str: String) = io.elements(str)
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
  behavior of "TestModule"
  it should "generate correct FIRRTL without global clock" in {
    val firrtl = chisel3.Driver.emit(() => new TestModule(() => new FakeDUT, useGlobalClk = false))
    val expectedFirrtlT =
      """|  module TestModule :
         |    input clock : Clock
         |    input reset : UInt<1>
         |    output io : {i : {flip a : Fixed<8><<5>>, flip b : UInt<4>, flip c : SInt<5>, flip clk : UInt<1>}, o : {a : Fixed<8><<5>>, b : UInt<4>, c : SInt<5>, clk : UInt<1>}}
         |
         |    clock is invalid
         |    reset is invalid
         |    io is invalid
         |    inst dut of FakeDUT @[TestModule.scala 21:19]
         |    dut.io is invalid
         |    dut.clock <= clock
         |    dut.reset <= reset
         |    node _T_21 = asClock(UInt<1>("h00")) @[TestModule.scala 26:29]
         |    dut.clock <= _T_21 @[TestModule.scala 26:18]
         |    dut.reset <= reset @[TestModule.scala 27:31]
         |    node _T_22 = asUInt(dut.io.o.clk) @[Utils.scala 115:25]
         |    node _T_23 = bits(_T_22, 0, 0) @[Utils.scala 115:32]
         |    io.o.clk <= _T_23 @[Utils.scala 115:15]
         |    node _T_24 = asSInt(dut.io.o.c) @[Utils.scala 102:25]
         |    io.o.c <= _T_24 @[Utils.scala 102:15]
         |    node _T_25 = asUInt(dut.io.o.b) @[Utils.scala 98:25]
         |    io.o.b <= _T_25 @[Utils.scala 98:15]
         |    node _T_26 = asFixedPoint(dut.io.o.a, 5) @[Utils.scala 108:37]
         |    io.o.a <= _T_26 @[Utils.scala 108:15]
         |    node _T_27 = asClock(io.i.clk) @[Utils.scala 111:25]
         |    dut.io.i.clk <= _T_27 @[Utils.scala 111:16]
         |    node _T_28 = asInterval(io.i.c, -10, 8, 0) @[Utils.scala 100:35]
         |    dut.io.i.c <= _T_28 @[Utils.scala 100:16]
         |    node_T_30 = cat(UInt<1>("h00"), io.i.b) @[Cat.scala 30:58]
         |    node _T_31 = asInterval(_T_30, 4, 8, 0) @[Utils.scala 96:50]
         |    dut.io.i.b <= _T_31 @[Utils.scala 96:16]
         |    node _T_32 = asInterval(io.i.a, -106, 71, 5) @[Utils.scala 104:35]
         |    dut.io.i.a <= _T_32 @[Utils.scala 104:16]""".stripMargin
    val expectedFirrtl = expectedFirrtlT.replaceAll(" ", "").split("\n").map(_.split("@").head).mkString("\n")
    val newFirrtl = firrtl.replaceAll(" ", "").split("\n").map(_.split("@").head).mkString("\n")
    println(newFirrtl)
    println(expectedFirrtl)
    require(newFirrtl.contains(expectedFirrtl))
  }
}