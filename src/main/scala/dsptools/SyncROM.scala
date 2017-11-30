package dsptools

import chisel3._
import chisel3.util.HasBlackBoxInline
import firrtl.ir.Type
import firrtl_interpreter._

import scala.collection.mutable

/*
 * This is inspired by Angie Wang's excellent UIntLut2D
 * The main difference comes from the fact that Xilinx can infer BRAMs for ROMs.
 * It can do single and dual port BRAMs. Xilinx publishes guidelines for how to write
 * verilog/vhdl in a way that allows the tool to infer BRAMs.
 * The basic idea is that it should be written using a case statement and the output should be registered
 */

class SyncROM(val blackboxName: String, val table: Seq[BigInt], val widthOverride: Option[Int] = None)
extends Module {
  val dataWidth = SyncROMBlackBox.dataWidth(table, widthOverride)

  val addrWidth = SyncROMBlackBox.addrWidth(table)

  val io = IO(new SyncROMIO(addrWidth=addrWidth, dataWidth=dataWidth))

  val rom = Module(new SyncROMBlackBox(blackboxName, table, widthOverride))

  rom.io.clock := clock
  rom.io.addr  := io.addr
  io.data      := rom.io.data
}

trait HasBlackBoxClock {
  val clock = Input(Clock())
}

class SyncROMIO(addrWidth: Int, dataWidth: Int) extends Bundle {
  val addr  = Input(UInt(addrWidth.W))
  val data  = Output(UInt(dataWidth.W))
}

class SyncROMBlackBox(blackboxName: String, table: Seq[BigInt], widthOverride: Option[Int] = None)
extends BlackBox with HasBlackBoxInline {
  val dataWidth = SyncROMBlackBox.dataWidth(table, widthOverride)

  val addrWidth = SyncROMBlackBox.addrWidth(table)

  val io = IO(new SyncROMIO(addrWidth=addrWidth, dataWidth = dataWidth) with HasBlackBoxClock)

  override def desiredName: String = blackboxName

  def tableEntry2CaseStr(value: BigInt, addr: BigInt): String = {
    s"$addrWidth'b${addr.toString(2)}: data <= $dataWidth'h${value.toString(16)};"
  }
  val tableStrings = table.zipWithIndex.map { case (t, i) => tableEntry2CaseStr(t, BigInt(i))}
  val tableString  = tableStrings.foldLeft("\n") { case (str, entry) => str + "      " + entry + "\n"}

  val verilog =
    s"""
       |module $name(
       |  input                                    clock,
       |  input      [${(addrWidth - 1).max(0)}:0] addr,
       |  output reg [${(dataWidth - 1).max(0)}:0] data
       |);
       |  always @(posedge clock) begin
       |    case (addr)$tableString
       |      default: data <= $dataWidth'h0;
       |    endcase
       |  end
       |endmodule
     """.stripMargin

  setInline(s"$name.v", verilog)

  SyncROMBlackBox.interpreterMap.update(name, (table, dataWidth))
}

object SyncROMBlackBox {
  def addrWidth(table: Seq[BigInt]): Int = {
    BigInt(table.length - 1).bitLength
  }
  def dataWidth(table: Seq[BigInt], widthOverride: Option[Int]): Int = {
    val w = widthOverride.getOrElse(table.map{_.bitLength}.max)
    require(w >= table.map{_.bitLength}.max, "width too small for table")
    w
  }
  private [dsptools] val interpreterMap = mutable.Map[String, (Seq[BigInt], Int)]()
}

// implementation for firrtl interpreter
class SyncROMBlackBoxImplementation(val name: String, val table: Seq[BigInt], dataWidth: Int) extends BlackBoxImplementation {
  var lastCycleAddr: BigInt    = BigInt(0)
  var currentCycleAddr: BigInt = BigInt(0)
  def cycle(): Unit = {
    println("cycle got called")
    lastCycleAddr = currentCycleAddr
  }

  def execute(inputValues: Seq[Concrete], tpe: Type, outputName: String): Concrete = {
    require(outputName == "data", s"Only output should be data, got $outputName")
    // TODO cycle() should do this
    lastCycleAddr = currentCycleAddr
    currentCycleAddr = inputValues.head.value
    val tableValue = if (lastCycleAddr.toInt < table.length) {
      table(lastCycleAddr.toInt)
    } else {
      BigInt(0) // default
    }
    ConcreteUInt(tableValue, dataWidth, inputValues.head.poisoned)
  }

  def outputDependencies(outputName: String) = outputName match {
    case "data" => Seq(fullName("addr"))
    case _      => Seq.empty
  }
}

class SyncROMBlackBoxFactory extends BlackBoxFactory {
  override def createInstance(instanceName: String, blackBoxName: String) =
  SyncROMBlackBox.interpreterMap.get(blackBoxName).map {
    case (table, dataWidth) => new SyncROMBlackBoxImplementation(instanceName, table, dataWidth)
  }
}