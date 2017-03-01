// See LICENSE for license details.

package dsptools.numbers

import chisel3._
import chisel3.util.HasBlackBoxResource

trait BlackBoxWithVerilog extends BlackBox with HasBlackBoxResource {
  def addVerilog(): Unit = {
    // Must be located in resources folder
    val blackBoxFloatVerilog = "/BlackBoxFloat.v"
    setResource(blackBoxFloatVerilog)
  }
}

class BlackboxOneOperand extends BlackBoxWithVerilog {
  val io = IO(new Bundle() {
    val in = Input(UInt(DspReal.underlyingWidth.W))
    val out = Output(UInt(DspReal.underlyingWidth.W))
  })
}

class BlackboxTwoOperand extends BlackBoxWithVerilog {
  val io = IO(new Bundle() {
    val in1 = Input(UInt(DspReal.underlyingWidth.W))
    val in2 = Input(UInt(DspReal.underlyingWidth.W))
    val out = Output(UInt(DspReal.underlyingWidth.W))
  })
}

class BlackboxTwoOperandBool extends BlackBoxWithVerilog {
  val io = IO(new Bundle() {
    val in1 = Input(UInt(DspReal.underlyingWidth.W))
    val in2 = Input(UInt(DspReal.underlyingWidth.W))
    val out = Output(Bool())
  })
}

class BBFAdd extends BlackboxTwoOperand { addVerilog() }

class BBFSubtract extends BlackboxTwoOperand { addVerilog() }

class BBFMultiply extends BlackboxTwoOperand { addVerilog() }

class BBFDivide extends BlackboxTwoOperand { addVerilog() }

class BBFGreaterThan extends BlackboxTwoOperandBool { addVerilog() }

class BBFGreaterThanEquals extends BlackboxTwoOperandBool { addVerilog() }

class BBFLessThan extends BlackboxTwoOperandBool { addVerilog() }

class BBFLessThanEquals extends BlackboxTwoOperandBool { addVerilog() }

class BBFEquals extends BlackboxTwoOperandBool { addVerilog() }

class BBFNotEquals extends BlackboxTwoOperandBool { addVerilog() }

/** Math operations from IEEE.1364-2005 **/
class BBFLn extends BlackboxOneOperand { addVerilog() }

class BBFLog10 extends BlackboxOneOperand { addVerilog() }

class BBFExp extends BlackboxOneOperand { addVerilog() }

class BBFSqrt extends BlackboxOneOperand { addVerilog() }

class BBFPow extends BlackboxTwoOperand { addVerilog() }

class BBFFloor extends BlackboxOneOperand { addVerilog() }

class BBFCeil extends BlackboxOneOperand { addVerilog() }

// Not supported by Verilator -- need to build out own approximation
class BBFSin extends BlackboxOneOperand { addVerilog() }

class BBFCos extends BlackboxOneOperand { addVerilog() }

class BBFTan extends BlackboxOneOperand { addVerilog() }

class BBFASin extends BlackboxOneOperand { addVerilog() }

class BBFACos extends BlackboxOneOperand { addVerilog() }

class BBFATan extends BlackboxOneOperand { addVerilog() }

class BBFATan2 extends BlackboxTwoOperand { addVerilog() }

class BBFHypot extends BlackboxTwoOperand { addVerilog() }

class BBFSinh extends BlackboxOneOperand { addVerilog() }

class BBFCosh extends BlackboxOneOperand { addVerilog() }

class BBFTanh extends BlackboxOneOperand { addVerilog() }

class BBFASinh extends BlackboxOneOperand { addVerilog() }

class BBFACosh extends BlackboxOneOperand { addVerilog() }

class BBFATanh extends BlackboxOneOperand { addVerilog() }

class BBFFromInt extends BlackBoxWithVerilog {
  val io = IO(new Bundle() {
    val in = Input(UInt(DspReal.underlyingWidth.W))
    val out = Output(UInt(DspReal.underlyingWidth.W))
  })
  addVerilog()
}

class BBFToInt extends BlackBoxWithVerilog {
  val io = IO(new Bundle() {
    val in = Input(UInt(DspReal.underlyingWidth.W))
    val out = Output(UInt(DspReal.underlyingWidth.W))
  })
  addVerilog()
}

// Not used
//class BBFIntPart extends BlackboxOneOperand { addVerilog() }