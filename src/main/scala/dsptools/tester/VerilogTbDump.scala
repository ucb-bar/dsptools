// SPDX-License-Identifier: Apache-2.0

package dsptools

import chisel3._
import chisel3.experimental._
import java.io.{BufferedWriter, File, FileWriter}

import DspTesterUtilities._
import chisel3.ActualDirection

// TODO: Get rid of
import chisel3.iotesters.TestersCompatibility

// Note: This will dump as long as genVerilogTb is true (even if you're peeking/poking DspReal)
trait VerilogTbDump {

  def dut: MultiIOModule

  // Used for getting target directory only (set in iotesters.Driver)
  val iotestersOM = chisel3.iotesters.Driver.optionsManager
  val targetDir = iotestersOM.targetDirName
  val tbFileName = s"${targetDir}/tb.v"
  val tb = new BufferedWriter(new FileWriter(tbFileName))

  val dsptestersOpt = dsptools.Driver.optionsManager.dspTesterOptions
  val verilogTb = dsptestersOpt.genVerilogTb

  val (inputs, outputs) = TestersCompatibility.getModuleNames(dut).filter({
    case (_, "clock") => false
    case (_, "reset") => false
    case _ => true
  }).partition({
    case (dat, _) =>
      DataMirror.directionOf(dat) == ActualDirection.Input
  })

  if (verilogTb) initVerilogTbFile()
  else deleteVerilogTbFile

  def initVerilogTbFile() {
    val dutName = dut.name

    val resetTime = dsptestersOpt.initTimeUnits
    // Input/output delay after which to peek/poke values
    val clkDelta = dsptestersOpt.clkMul * dsptestersOpt.inOutDelay

    tb write s"// SPDX-License-Identifier: Apache-2.0\n"
    tb write s"// Example VCS Command: $$VCS_HOME/bin/vcs -debug_pp -full64 +define+UNIT_DELAY +rad +v2k +vcs+lic+wait " +
    s"+vc+list +vcs+initreg+random +vcs+dumpvars+out.vcd tb.v ${dutName}.v ...\n"
    
    tb write s"`timescale ${dsptestersOpt.tbTimeUnitPs}ps / ${dsptestersOpt.tbTimePrecisionPs}ps\n"
    tb write s"\n`define CLK_PERIOD ${dsptestersOpt.clkMul}\n"
    tb write s"\n`define HALF_CLK_PERIOD ${dsptestersOpt.clkMul.toDouble/2}\n"
    tb write s"`define RESET_TIME ${resetTime}\n"
    tb write s"`define INIT_TIME ${clkDelta + resetTime}\n"

    tb write "`define expect(nodeName, nodeVal, expVal, cycle) if (nodeVal !== expVal) begin " +
      "\\\n  $display(\"\\t ASSERTION ON %s FAILED @ CYCLE = %d, 0x%h != EXPECTED 0x%h\", " +
      "\\\n  nodeName,cycle,nodeVal,expVal); $stop; end\n\n"

    tb write "module testbench_v;\n\n"

    tb write "  integer cycle = 0;\n\n"

    tb write "  reg clock = 1;\n"
    tb write "  reg reset = 1;\n"
      
    inputs foreach { case (node, name) =>
      val s = signPrefix(node)
      tb write s"  reg$s[${node.getWidth-1}:0] $name = 0;\n"
    }
    outputs foreach { case (node, name) =>
      val s = signPrefix(node)
      tb write s"  wire$s[${node.getWidth-1}:0] ${name};\n"
    }

    tb write "\n  always #`HALF_CLK_PERIOD clock = ~clock;\n"

    tb write "\n  initial begin\n"
    tb write "    #`RESET_TIME\n"
    tb write "    forever #`CLK_PERIOD cycle = cycle + 1;\n"
    tb write "  end\n\n"

    tb write s"  ${dutName} ${dutName}(\n"
    tb write "    .clock(clock),\n"
    tb write "    .reset(reset),\n"
    tb write ((inputs ++ outputs).unzip._2 map (name => s"    .${name}(${name})") mkString ",\n")
    tb write ");\n\n"

    // Inputs fed delta after clk rising edge; read delta after clk rising edge
    tb write "  initial begin\n"
    tb write "    #`INIT_TIME reset = 0;\n"
  }

  def deleteVerilogTbFile() {
    tb.close()
    new File(tbFileName).delete()
  }

  def finishVerilogTb() {
    if (verilogTb) {
      tb write "\n    #`CLK_PERIOD $display(\"\\t **Ran through all test vectors**\"); $finish;\n"
      tb write "\n  end\n"
      tb write "endmodule"
      tb.close()  
    }
  }

  def stepPrint(n: Int) {
    if (verilogTb) tb write s"    #($n*`CLK_PERIOD) "
  }

  def resetPrint(n: Int) {
    if (verilogTb) tb write s"    reset = 1; #($n*`CLK_PERIOD) reset = 0;\n"
  }

  def pokePrint(signal: Element, value: BigInt) {
    if (verilogTb) {
      val matchingInput = inputs find (_._1 == signal)
      matchingInput match {
        case Some((_, name)) => {
          // Don't have 0-width inputs (issue for >= #'s)
          val bitLength = value.bitLength.max(1) 
          val id = if (value >= 0) s"$bitLength\'d" else ""
          tb write s"    $name = $id$value;\n"
        }
        case _ => // Don't print if not input
      }
    }
  }

  def peekPrint(signal: Element, res: BigInt) {
    if (verilogTb) {
      val matchingOutput = outputs find (_._1 == signal)
      matchingOutput match {
        case Some((_, name)) => {
          //nodeName, nodeVal, expVal, cycle
          tb write "    `expect(\"%s\",%s,%d,cycle)\n".format(name,name,res)
        }
        case _ => // Don't print if not output
      }
    }
  }

}
