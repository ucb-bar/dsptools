package dspblocks

import breeze.math.Complex
import chisel3._
import chisel3.experimental.FixedPoint
import chisel3.internal.firrtl.KnownBinaryPoint
import dsptools._
import dsptools.numbers._
import freechips.rocketchip.amba.apb.APBMasterModel
import freechips.rocketchip.amba.axi4.AXI4MasterModel
import freechips.rocketchip.tilelink.TLMasterModel
import spire.math.ConvertableFrom
import spire.implicits._


trait MemTester {
  def resetMem(): Unit
  def readAddr(addr: BigInt): BigInt
  def writeAddr(addr: BigInt, value: BigInt): Unit
  def writeAddr(addr: Int, value: Int): Unit = writeAddr(BigInt(addr), BigInt(value))
}

trait TLMemTester extends TLMasterModel {
  def resetMem(): Unit = {
    tlReset()
  }

  def readAddr(addr: BigInt): BigInt = {
    tlReadWord(addr)
  }

  def writeAddr(addr: BigInt, value: BigInt): Unit = {
    tlWriteWord(addr, value)
  }
}

trait APBMemTester extends APBMasterModel {
  def resetMem(): Unit = {
    apbReset()
  }

  def readAddr(addr: BigInt): BigInt = {
    apbRead(addr)
  }

  def writeAddr(addr: BigInt, value: BigInt): Unit = {
    apbWrite(addr, value)
  }
}

trait AXI4MemTester extends AXI4MasterModel {
  def resetMem(): Unit = {
    axiReset()
  }

  def readAddr(addr: BigInt): BigInt = {
    axiReadWord(addr)
  }

  def writeAddr(addr: BigInt, value: BigInt): Unit = {
    axiWriteWord(addr, value)
  }
}

/*
trait AHBMemTester[T <: MultiIOModule] extends AHBMasterModel[T] {
  def resetMem(): Unit = {
    ahbReset()
  }

  def readAddr(addr: BigInt): BigInt = {
    ahbReadWord(addr)
  }

  def writeAddr(addr: BigInt, value: BigInt): Unit = {
    ahbWriteWord(addr, value)
  }
}
*/


object PeekPokePackers {
  def signedToUnsigned(in: BigInt, width: Int): BigInt = {
    require(width >= 0)
    if (width == 0) return BigInt(0)
    if (in >= BigInt(0)) {
      in
    } else {
      (BigInt(1) << width) + in
    }
  }
  def unsignedToSigned(in: BigInt, width: Int): BigInt = {
    require(width >= 0)
    if (width == 0) return 0
    val msb = BigInt(1) << (width - 1)
    if (in < msb) {
      in
    } else {
      in - (msb << 1)
    }
  }

  def pack[T <: Data, V : ConvertableFrom](value: V, gen: T): BigInt = gen match {
    case _:DspReal => DspTesterUtilities.doubleToBigIntBits(value.toDouble())
    case f:FixedPoint => f.binaryPoint match {
      case KnownBinaryPoint(bp) =>
        val bigIntValue = FixedPoint.toBigInt(value.toDouble(), bp)
        signedToUnsigned(bigIntValue, gen.getWidth)
      case _ => throw DspException("Must poke FixedPoint with known binary point")
    }
    case _: UInt => value.toBigInt
    case _: SInt => signedToUnsigned(value.toBigInt, gen.getWidth)
  }

  def packDouble[T <: Data](value: Double, gen: T): BigInt = pack(value, gen)
  def packInt[T <: Data](value: Int, gen: T): BigInt = pack(value, gen)
  def packBigInt[T <: Data](value: BigInt, gen: T): BigInt = pack(value, gen)

  def pack[T <: Data](value: Complex, gen: DspComplex[T]): BigInt = {
    val real = packDouble(value.real, gen.real)
    val imag = packDouble(value.imag, gen.imag)
    (real << gen.imag.getWidth) | imag
  }

  def pack[T <: Data](value: Seq[Complex], gen: Seq[DspComplex[T]]): BigInt = {
    value.zip(gen).foldLeft(BigInt(0)) { case (bi, (elem, genElem)) =>
      (bi << genElem.getWidth) | pack(elem, genElem)
    }
  }

  def unpackDouble[T <: Data](value: BigInt, gen: T): Double = gen match {
    case _:DspReal => DspTesterUtilities.bigIntBitsToDouble(value)
    case f:FixedPoint => f.binaryPoint match {
      case KnownBinaryPoint(b) => FixedPoint.toDouble(unsignedToSigned(value, f.getWidth), b)
      case _ => throw DspException("Must poke FixedPoint with known binary point")
    }
    case _:UInt => value.toDouble
    case _:SInt => unsignedToSigned(value, gen.getWidth).toDouble
  }

  def unpack[T <: Data](value: BigInt, gen: DspComplex[T]): Complex = {
    val real = unpackDouble(value >> gen.imag.getWidth, gen.real)
    val imag = unpackDouble(value &  ((BigInt(1) << gen.imag.getWidth) - 1), gen.imag)
    Complex(real, imag)
  }

  def unpack[T <: Data](values: Seq[BigInt], gen: Seq[DspComplex[T]]): Seq[Complex] = {
    values.zip(gen).map { case (v, g) => unpack(v, g) }
  }


}
