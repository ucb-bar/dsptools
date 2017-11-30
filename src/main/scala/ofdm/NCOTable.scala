package ofdm

import breeze.numerics.sin
import chisel3._
import chisel3.experimental.FixedPoint
import chisel3.util._
import dsptools.SyncROM
import dsptools.numbers._

import scala.collection.mutable
import scala.math.Pi


private [ofdm] object NCOTableParams {
  val tableNameList = mutable.Set[String]()
}

case class NCOTableParams[T <: Data]
(
  phaseWidth: Int,
  phaseConv: UInt => T,
  protoTable: T,
  protoOut:   T,
  tableSize: Int,
  nInterpolationTerms: Int = 0,
  tableName: String = "NCOTableLUT"
) {
  require(tableSize > 0)
  require(isPow2(tableSize))
  require(nInterpolationTerms >= 0)

  require(!NCOTableParams.tableNameList.contains(tableName), s"Name $tableName already used")
  NCOTableParams.tableNameList.add(tableName)

  val addrBits = log2Ceil(tableSize)
  require(phaseWidth >= addrBits + 2,
    s"Input phase must have at least two more bits ($phaseWidth) than the address into the table ($addrBits)")

}

object FixedNCOTableParams {
  def apply(phaseWidth: Int, tableSize: Int, tableWidth: Int, nInterpolationTerms: Int = 0): NCOTableParams[FixedPoint] = NCOTableParams(
    phaseWidth = phaseWidth,
    phaseConv = {x: UInt => Cat(0.U, x).asTypeOf(FixedPoint.apply(width = phaseWidth.W, binaryPoint = phaseWidth.BP))},
    protoTable = FixedPoint((tableWidth + 2).W, tableWidth.BP),
    protoOut   = FixedPoint((tableWidth + 2).W, tableWidth.BP),
    tableSize  = tableSize,
    nInterpolationTerms = nInterpolationTerms
  )
}


class NCOTableIO[T <: Data](params: NCOTableParams[T]) extends Bundle {
  val phase = Input(UInt(params.phaseWidth.W))
  val sinOut = Output(params.protoOut.cloneType)
  val cosOut = Output(params.protoOut.cloneType)
}

class NCOTable[T <: Data : Ring : BinaryRepresentation : ConvertableTo](params: NCOTableParams[T]) extends Module {
  val io = IO(new NCOTableIO(params))

  // quarter wave
  val sinTable = (0 until params.tableSize).map { i =>
    val sinValue = sin(0.5 * Pi * (i.toDouble) / params.tableSize)
    val asT = ConvertableTo[T].fromDouble(sinValue, params.protoTable)
    asT.litValue()
  }

  val table0 = Module(new SyncROM(params.tableName, sinTable))
  val table1 = Module(new SyncROM(params.tableName, sinTable))

  // quantize phase for table lookup
  val totalWidth = params.phaseWidth
  // val totalMask = (BigInt(1) << totalWidth) - 1

  val msbTop    = totalWidth
  val msbBot    = totalWidth - 2
  val addrTop   = msbBot
  val addrBot   = msbBot - log2Ceil(params.tableSize)
  val interpTop = addrBot
  val interpBot = 0

  val msbs   = io.phase(msbTop-1, msbBot)
  val addr   = io.phase(addrTop-1, addrBot)
  val interp = io.phase(0 max (interpTop-1), interpBot)
  // interpret the lsbs as a Δθ
  val x     = params.phaseConv(interp)

  val sinAddr = Wire(UInt())
  val cosAddr = Wire(UInt())
  val sinNegative = Wire(Bool())
  val cosNegative = Wire(Bool())

  val one = ConvertableTo[T].fromDouble(1.0)

  def addrReverse(addr: UInt): UInt = {
    0.U - addr
    //(~(addr - 1.U)).asUInt()
  }

  def zeroAddrReverse(addr: UInt): UInt = {
    //0.U - addr
    (~addr).asUInt()
  }

  val sinIsOne = Reg(Bool())
  val cosIsOne = Reg(Bool())

  sinIsOne := false.B
  cosIsOne := false.B

  switch(msbs) {
    is("b00".U) {
      sinAddr := addr
      cosAddr := addrReverse(addr)
      sinNegative := false.B
      cosNegative := false.B
      cosIsOne := cosAddr === 0.U && addr === 0.U
    }
    is("b01".U) {
      sinAddr := addrReverse(addr)
      cosAddr :=  addr
      sinNegative := false.B
      cosNegative := true.B
      sinIsOne := sinAddr === 0.U && addr === 0.U
    }
    is("b10".U) {
      sinAddr := addr
      cosAddr := addrReverse(addr)
      sinNegative := true.B
      cosNegative := true.B
      cosIsOne := cosAddr === 0.U && addr === 0.U
    }
    is("b11".U) {
      sinAddr := addrReverse(addr)
      cosAddr := addr
      sinNegative := true.B
      cosNegative := false.B
      sinIsOne := sinAddr === 0.U && addr === 0.U
    }


  }

  table0.io.addr := sinAddr
  table1.io.addr := cosAddr

  val sinTableOut = Mux(sinIsOne, one, Cat(0.U, table0.io.data).asTypeOf(params.protoTable))
  val cosTableOut = Mux(cosIsOne, one, Cat(0.U, table1.io.data).asTypeOf(params.protoTable))

  val sinOut = Mux(sinNegative, -sinTableOut, sinTableOut)
  val cosOut = Mux(cosNegative, -cosTableOut, cosTableOut)

  // interpolation
  // coeffs go down as 1/n! * a_n
  // for sine, a_n is cos, -sin, -cos, sin, cos, ...
  // for cosine, a_n is the same sequence with the first term dropped

  val sinDerivs = Seq(cosOut, -sinOut, -cosOut, sinOut)
  val cosDerivs = Seq(-sinOut, -cosOut, sinOut, cosOut)

  def fact(i: Int): Double = {
    if (i <= 0) 1.0
    else (1 to i).map(BigInt(_)).product.toDouble
  }


  val n = params.protoOut match {
    case f: FixedPoint => f.binaryPoint.get
    case _ => 64
  }

  // compute terms of the taylor approximation
  val (sinTerms, cosTerms) = (Seq((sinOut, cosOut)) ++  (1 to params.nInterpolationTerms).map { i =>

    val coeff = ConvertableTo[T].fromDouble(math.pow(2.0 * Pi, i) / fact(i) /* / math.pow(Pi, i) */ , params.protoOut)
    // coeff * x^i
    val coeff_x_xi = (coeff * TreeReduce(Seq.fill(i) {
      x
    }, (x: T, y: T) => (x * y).trimBinary(n))).trimBinary(n)


    val sinTerm = (coeff_x_xi * sinDerivs((i - 1) % 4)).trimBinary(n)
    val cosTerm = (coeff_x_xi * cosDerivs((i - 1) % 4)).trimBinary(n)

    (sinTerm, cosTerm)
  }).unzip

  // add all the terms
  val sinInterp = TreeReduce(sinTerms, (x:T, y:T) => (x+y).trimBinary(n))
  val cosInterp = TreeReduce(cosTerms, (x:T, y:T) => (x+y).trimBinary(n))

  io.sinOut := sinInterp
  io.cosOut := cosInterp
}

