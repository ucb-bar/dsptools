// SPDX-License-Identifier: Apache-2.0

package dsptools.numbers

import chisel3._
import chisel3.util.HasBlackBoxResource

/*
 * Uses classname to find verilog implementation of blackbox
 */
trait BlackBoxWithVerilog extends BlackBox with HasBlackBoxResource {
  addResource("/" + this.getClass.getSimpleName + ".v")
}

class BlackboxOneOperand extends BlackBoxWithVerilog {
  val io = IO(new Bundle() {
    val in = Input(UInt(DspReal.underlyingWidth.W))
    val out = Output(UInt(DspReal.underlyingWidth.W))
  })
  io.suggestName("io")
}

class BlackboxTwoOperand extends BlackBoxWithVerilog {
  val io = IO(new Bundle() {
    val in1 = Input(UInt(DspReal.underlyingWidth.W))
    val in2 = Input(UInt(DspReal.underlyingWidth.W))
    val out = Output(UInt(DspReal.underlyingWidth.W))
  })
  io.suggestName("io")
}

class BlackboxTwoOperandBool extends BlackBoxWithVerilog {
  val io = IO(new Bundle() {
    val in1 = Input(UInt(DspReal.underlyingWidth.W))
    val in2 = Input(UInt(DspReal.underlyingWidth.W))
    val out = Output(Bool())
  })
  io.suggestName("io")
}

class BBFAdd extends BlackboxTwoOperand

class BBFSubtract extends BlackboxTwoOperand

class BBFMultiply extends BlackboxTwoOperand

class BBFDivide extends BlackboxTwoOperand

class BBFGreaterThan extends BlackboxTwoOperandBool

class BBFGreaterThanEquals extends BlackboxTwoOperandBool

class BBFLessThan extends BlackboxTwoOperandBool

class BBFLessThanEquals extends BlackboxTwoOperandBool

class BBFEquals extends BlackboxTwoOperandBool

class BBFNotEquals extends BlackboxTwoOperandBool

/** Math operations from IEEE.1364-2005 **/
class BBFLn extends BlackboxOneOperand

class BBFLog10 extends BlackboxOneOperand

class BBFExp extends BlackboxOneOperand

class BBFSqrt extends BlackboxOneOperand

class BBFPow extends BlackboxTwoOperand

class BBFFloor extends BlackboxOneOperand

class BBFCeil extends BlackboxOneOperand

// Not supported by Verilator -- need to build out own approximation
class BBFSin extends BlackboxOneOperand

class BBFCos extends BlackboxOneOperand

class BBFTan extends BlackboxOneOperand

class BBFASin extends BlackboxOneOperand

class BBFACos extends BlackboxOneOperand

class BBFATan extends BlackboxOneOperand

class BBFATan2 extends BlackboxTwoOperand

class BBFHypot extends BlackboxTwoOperand

class BBFSinh extends BlackboxOneOperand

class BBFCosh extends BlackboxOneOperand

class BBFTanh extends BlackboxOneOperand

class BBFASinh extends BlackboxOneOperand

class BBFACosh extends BlackboxOneOperand

class BBFATanh extends BlackboxOneOperand

class BBFFromInt extends BlackboxOneOperand

class BBFToInt extends BlackboxOneOperand

// Not used
//class BBFIntPart extends BlackboxOneOperand { addVerilog() }
