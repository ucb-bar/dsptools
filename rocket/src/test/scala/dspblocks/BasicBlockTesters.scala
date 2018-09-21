// See LICENSE for license details.

package dspblocks

import amba.apb.APBMasterModel
import amba.axi4.AXI4MasterModel
import chisel3._
import chisel3.iotesters.PeekPokeTester
import freechips.rocketchip.amba.axi4stream._
import freechips.rocketchip.tilelink.{TLBundle, TLMasterModel}

abstract class PassthroughTester[D, U, EO, EI, B <: Data](dut: Passthrough[D, U, EO, EI, B] with StandaloneBlock[D, U, EO, EI, B])
extends PeekPokeTester(dut.module) with MemTester with AXI4StreamModel {
  resetMem()

  val in = dut.in.getWrappedValue
  val out = dut.out.getWrappedValue

  val master = bindMaster(in)
  val slave = bindSlave(out)

  step(5)

  val depth = readAddr(BigInt(0)).toInt
  val expectedDepth = dut.params.depth
  expect(depth == expectedDepth, s"Depth was $depth, should be $expectedDepth")

  // fill queue
  master.addTransactions((0 until expectedDepth).map(x => AXI4StreamTransaction(data = x)))
  stepToCompletion()

  // queue should be full
  expect(in.ready, 0)
  expect(out.valid, 1)

  // empty queue
  slave.addExpects((0 until expectedDepth).map(x => AXI4StreamTransactionExpect(data = Some(x))))
  stepToCompletion()

  // should be done
  expect(out.valid, 0)
}

class AXI4PassthroughTester(c: AXI4Passthrough with AXI4StandaloneBlock)
  extends PassthroughTester(c) with AXI4MemTester {
  def memAXI = c.ioMem.get.getWrappedValue
}

class APBPassthroughTester(c: APBPassthrough with APBStandaloneBlock)
  extends PassthroughTester(c) with APBMemTester {
  def memAPB = c.ioMem.get.getWrappedValue
}

class TLPassthroughTester(c: TLPassthrough with TLStandaloneBlock)
  extends PassthroughTester(c) with TLMemTester {
  override def memTL: TLBundle = c.ioMem.get.getWrappedValue
}

abstract class ByteRotateTester[D, U, EO, EI, B <: Data] (dut: ByteRotate[D, U, EO, EI, B] with
  StandaloneBlock[D, U, EO, EI, B]) extends PeekPokeTester(dut.module) with MemTester with AXI4StreamModel {
  val in  = dut.in.getWrappedValue
  val out = dut.out.getWrappedValue
  val n = in.bits.params.n

  resetMem()
  val master = bindMaster(in)
  val slave = bindSlave(out)

  val toShift = BigInt(1)

  step(5)

  for (rot <- 0 until n) {
    writeAddr(0, rot)
    // fill queue
    def shiftRot(in: Int): Int = {
      (n + in - rot) % n
    }
    slave.addExpects((0 until n).map(x => AXI4StreamTransactionExpect(data = Some(toShift << (8 * shiftRot(x))))))
    step(1)
    master.addTransactions((0 until n).map(x => AXI4StreamTransaction(data = toShift << (8 * x))))

    stepToCompletion()
  }

  // should be done
  expect(out.valid, 0)
}

class AXI4ByteRotateTester(c: AXI4ByteRotate with AXI4StandaloneBlock) extends ByteRotateTester(c) with AXI4MemTester {
  def memAXI = c.ioMem.get.getWrappedValue
}

class APBByteRotateTester(c: APBByteRotate with APBStandaloneBlock) extends ByteRotateTester(c) with APBMemTester {
  def memAPB = c.ioMem.get.getWrappedValue
}

class TLByteRotateTester(c: TLByteRotate with TLStandaloneBlock) extends ByteRotateTester(c) with TLMemTester {
  def memTL = c.ioMem.get.getWrappedValue
}


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