package amba.axi4stream

import chisel3.experimental.BaseModule
import chisel3.iotesters.PeekPokeTester
import chisel3.util.IrrevocableIO
import freechips.rocketchip.amba.axi4stream.AXI4StreamBundle


case class AXI4StreamTransaction
(
  data: BigInt = 0,
  last: Boolean = false,
  strb: BigInt = -1,
  keep: BigInt = -1,
  user: BigInt = 0,
  id:   BigInt = 0,
  dest: BigInt = 0
)

case class AXI4StreamTransactionExpect
(
  data: Option[BigInt]  = None,
  last: Option[Boolean] = None,
  strb: Option[BigInt]  = None,
  keep: Option[BigInt]  = None,
  user: Option[BigInt]  = None,
  id:   Option[BigInt]  = None,
  dest: Option[BigInt]  = None
)


class AXI4StreamPeekPokeMaster(port: AXI4StreamBundle, tester: PeekPokeTester[_]) {
  protected var input: Seq[AXI4StreamTransaction] = Seq()

  def addTransactions(in: Seq[AXI4StreamTransaction]): Unit = {
    input ++= in
  }

  def step(): Unit = {
    import tester.{peek, poke}
    if (input.isEmpty) {
      poke(port.valid, 0)
    } else {
      val t = input.head
      poke(port.valid, 1)
      poke(port.bits.data, t.data)
      poke(port.bits.last, if (t.last) 1 else 0)
      if (t.strb == -1) {
        val allOnes = (BigInt(1) << port.bits.strb.getWidth) - 1
        poke(port.bits.strb, allOnes)
      } else {
        poke(port.bits.strb, t.strb)
      }
      if (t.keep == -1) {
        val allOnes = (BigInt(1) << port.bits.keep.getWidth) - 1
        poke(port.bits.keep, allOnes)
      } else {
        poke(port.bits.keep, t.keep)
      }
      poke(port.bits.user, t.user)
      poke(port.bits.id,   t.id)
      poke(port.bits.dest, t.dest)
      if (peek(port.ready) != BigInt(0)) {
        input = input.tail
      }
    }
  }

  def complete(): Boolean = {
    input.isEmpty
  }
}


class AXI4StreamPeekPokeSlave(port: AXI4StreamBundle, tester: PeekPokeTester[_]) {

  protected var output: Seq[AXI4StreamTransaction] = Seq()
  protected var expects: Seq[AXI4StreamTransactionExpect] = Seq()

  def addExpects(expect: Seq[AXI4StreamTransactionExpect]): Unit = {
    expects ++= expect
  }

  def getTransactions(): Seq[AXI4StreamTransaction] = {
    val toret = output
    output = Seq()
    toret
  }

  def expect(port: AXI4StreamBundle, value: AXI4StreamTransactionExpect): Boolean = {
    import tester.{expect => texpect}
    value.data.map   { texpect(port.bits.data, _) }.getOrElse(true) &&
      value.last.map { x => texpect(port.bits.last, if (x) 1 else 0) }.getOrElse(true) &&
      value.strb.map { texpect(port.bits.strb, _) }.getOrElse(true) &&
      value.keep.map { texpect(port.bits.keep, _) }.getOrElse(true) &&
      value.user.map { texpect(port.bits.user, _) }.getOrElse(true) &&
      value.id.map   { texpect(port.bits.id,   _) }.getOrElse(true) &&
      value.dest.map { texpect(port.bits.dest, _) }.getOrElse(true)
  }

  def step(): Unit = {
    import tester.{peek, poke}
    if (expects.isEmpty) {
      poke(port.ready, 0)
    } else {
      poke(port.ready, 1)
      if (peek(port.valid) != BigInt(0)) {
        expect(port, expects.head)
        expects = expects.tail

        output +:= AXI4StreamTransaction(
          data = peek(port.bits.data),
          last = peek(port.bits.last) != BigInt(0),
          strb = peek(port.bits.strb),
          keep = peek(port.bits.keep),
          user = peek(port.bits.user),
          id   = peek(port.bits.id),
          dest = peek(port.bits.dest)
        )
      }
    }
  }

  def complete(): Boolean = {
    expects.isEmpty
  }
}

trait AXI4StreamMasterModel[T <: BaseModule] extends PeekPokeTester[T] {
  protected var masters: Seq[AXI4StreamPeekPokeMaster] = Seq()

  def resetMaster(port: AXI4StreamBundle): Unit = {
    poke(port.valid, 0)
  }

  def bindMaster(port: AXI4StreamBundle): AXI4StreamPeekPokeMaster = {
    resetMaster(port)
    val master = new AXI4StreamPeekPokeMaster(port, this)
    masters +:= master
    master
  }

  protected def stepMasters(): Unit = {
    masters.foreach(_.step())
  }

  override def step(n: Int): Unit = {
    for (i <- 0 until n) {
      stepMasters()
      super.step(1)
    }
  }

  def mastersComplete(): Boolean = {
    masters.map(_.complete()).reduce(_ && _)
  }

  def stepToCompletion(maxCycles: Int = 1000): Unit = {
    for (i <- 0 until maxCycles) {
      if (masters.isEmpty) {
        step(1)
        return
      } else {
        step(1)
      }
    }
  }
}

trait AXI4StreamSlaveModel[T <: BaseModule] extends PeekPokeTester[T] {
  protected var slaves: Seq[AXI4StreamPeekPokeSlave] = Seq()

  def resetSlave(port: AXI4StreamBundle): Unit = {
    poke(port.ready, 0)
  }

  def bindSlave(port: AXI4StreamBundle): AXI4StreamPeekPokeSlave = {
    resetSlave(port)
    val slave = new AXI4StreamPeekPokeSlave(port, this)
    slaves +:= slave
    slave
  }

  protected def stepSlaves(): Unit = {
    slaves.foreach(_.step())
  }

  override def step(n: Int): Unit = {
    for (i <- 0 until n) {
      stepSlaves()
      super.step(1)
    }
  }

  def stepToCompletion(maxCycles: Int = 1000): Unit = {
    for (i <- 0 until maxCycles) {
      if (slaves.isEmpty) {
        step(1)
        return
      } else {
        step(1)
      }
    }
  }

  def slavesComplete(): Boolean = {
    slaves.map(_.complete()).reduce(_ && _)
  }
}

trait AXI4StreamModel[T <: BaseModule] extends
  AXI4StreamSlaveModel[T] with AXI4StreamMasterModel[T] {

  override def step(n: Int): Unit = {
    for (i <- 0 until n) {
      stepMasters()
      super[AXI4StreamSlaveModel].step(1)
    }
  }

  override def stepToCompletion(maxCycles: Int = 1000): Unit = {
    for (i <- 0 until maxCycles) {
      if (slavesComplete() && mastersComplete()) {
        step(1)
        return
      } else {
        step(1)
      }
    }
  }
}