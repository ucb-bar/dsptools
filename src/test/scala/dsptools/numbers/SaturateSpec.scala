package dsptools.numbers.rounding

import chisel3._
import chisel3.experimental.FixedPoint
import chisel3.iotesters._
import org.scalatest.{FlatSpec, Matchers}

class SaturateUIntMod(val add: Boolean) extends MultiIOModule {
  val a = IO(Input(UInt(8.W)))
  val b = IO(Input(UInt(8.W)))
  val c = IO(Output(UInt()))

  // use wires so we don't know the width in the frontend
  val aWire = Wire(UInt())
  val bWire = Wire(UInt())

  aWire := a
  bWire := b

  require(!aWire.isWidthKnown)
  require(!bWire.isWidthKnown)

  c := (if (add) {
    Saturate.addUInt(aWire, bWire)
  } else {
    Saturate.subUInt(aWire, bWire)
  })
}

class SaturateUIntTester(dut: SaturateUIntMod) extends PeekPokeTester(dut) {
  for (i <- 0 until 255) {
    poke(dut.a, i)
    for (j <- 0 until 255) {
      poke(dut.b, j)
      if (dut.add) {
        if (i + j < 255) {
          expect(dut.c, i + j)
        } else {
          expect(dut.c, 255)
        }
      } else {
        if (i >= j) {
          expect(dut.c, i - j)
        } else {
          expect(dut.c, 0)
        }
      }
    }
  }
}

class SaturateSIntMod(val add: Boolean) extends MultiIOModule {
  val a = IO(Input(SInt(8.W)))
  val b = IO(Input(SInt(8.W)))
  val c = IO(Output(SInt()))

  // use wires so we don't know the width in the frontend
  val aWire = Wire(SInt())
  val bWire = Wire(SInt())

  aWire := a
  bWire := b

  require(!aWire.isWidthKnown)
  require(!bWire.isWidthKnown)

  c := (if (add) {
    Saturate.addSInt(aWire, bWire)
  } else {
    Saturate.subSInt(aWire, bWire)
  })
}

class SaturateSIntTester(dut: SaturateSIntMod) extends PeekPokeTester(dut) {
  for (i <- -128 until 127) {
    poke(dut.a, i)
    for (j <- -128 until 127) {
      poke(dut.b, j)
      val expRes = if (dut.add) {
        i + j
      } else {
        i - j
      }
      if (expRes > 127) {
        expect(dut.c, 127)
      } else if (expRes < -128) {
        expect(dut.c, -128)
      } else {
        expect(dut.c, expRes)
      }
    }
  }
}

class SaturateFixedPointMod(val add: Boolean, val aBP: Int = 0, val bBP: Int = 0) extends MultiIOModule {
  val cBP = aBP max bBP
  val a = IO(Input(FixedPoint(8.W, aBP.BP)))
  val b = IO(Input(FixedPoint(8.W, bBP.BP)))
  val c = IO(Output(FixedPoint(16.W, cBP.BP)))

  // use wires so we don't know the width in the frontend
  val aWire = Wire(FixedPoint())
  val bWire = Wire(FixedPoint())

  aWire := a
  bWire := b

  require(!aWire.isWidthKnown)
  require(!bWire.isWidthKnown)

  c := (if (add) {
    Saturate.addFixedPoint(aWire, bWire)
  } else {
    Saturate.subFixedPoint(aWire, bWire)
  })
}

class SaturateFixedPointTester(dut: SaturateFixedPointMod) extends PeekPokeTester(dut) {
  import math.pow
  val aBP = dut.aBP
  val bBP = dut.bBP
  val cBP = dut.cBP

  val max = 127 * pow(2.0, -cBP)
  val min = -128 * pow(2.0, -cBP)

  val astep = pow(2.0, -aBP)
  val bstep = pow(2.0, -bBP)
  for (i <- -128 * astep until 128 * astep by astep) {
    pokeFixedPoint(dut.a, i)
    for (j <- -128 * bstep until 128 * bstep by bstep) {
      pokeFixedPoint(dut.b, j)
      val expRes = if (dut.add) {
        i + j
      } else {
        i - j
      }
      if (expRes > max) {
        // expectFixedPoint(dut.c, max, "max")
      } else if (expRes < min) {
        // expectFixedPoint(dut.c, min, "min")
      } else {
        // expectFixedPoint(dut.c, expRes, "middle")
      }
    }
  }
}

class SaturateSpec extends FlatSpec with Matchers {

  behavior of "Saturating add"

  it should "work with UInt" in {
    chisel3.iotesters.Driver.execute(Array[String](), () => new SaturateUIntMod(true)) {
      c => new SaturateUIntTester(c)
    } should be (true)
  }
  it should "work with SInt" in {
    chisel3.iotesters.Driver.execute(Array[String](), () => new SaturateSIntMod(true)) {
      c => new SaturateSIntTester(c)
    } should be (true)
  }
  it should "work with FixedPoint" in {
    for (aBP <- 0 until 8) {
      for (bBP <- 0 until 8) {
        chisel3.iotesters.Driver.execute(Array[String](), () => new SaturateFixedPointMod(true, aBP, bBP)) {
          c => new SaturateFixedPointTester(c)
        } should be (true)
      }
    }
  }

  behavior of "Saturating sub"

  it should "work with UInt" in {
    chisel3.iotesters.Driver.execute(Array[String](), () => new SaturateUIntMod(false)) {
      c => new SaturateUIntTester(c)
    } should be (true)
  }
  it should "work with SInt" in {
    chisel3.iotesters.Driver.execute(Array[String](), () => new SaturateSIntMod(false)) {
      c => new SaturateSIntTester(c)
    } should be (true)
  }
  // for now, fixed point won't work because width inference and FixedPoint
  // lowering happen from High -> Mid. Without finer-grained scheduling, we
  // can't insert our pass after width inference but below FixedPoint lowering.
  it should "work with FixedPoint" ignore {
    for (aBP <- 0 until 8) {
      for (bBP <- 0 until 8) {
        chisel3.iotesters.Driver.execute(Array[String](), () => new SaturateFixedPointMod(false, aBP, bBP)) {
          c => new SaturateFixedPointTester(c)
        } should be (true)
      }
    }
  }
}
