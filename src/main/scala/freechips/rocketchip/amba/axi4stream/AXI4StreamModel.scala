package freechips.rocketchip.amba.axi4stream

import breeze.stats.distributions._
import chisel3.MultiIOModule
import chisel3.iotesters.PeekPokeTester

import scala.language.implicitConversions

object DoubleToBigIntRand {
  implicit def apply(r: Rand[Double]): Rand[BigInt] = new Rand[BigInt] {
    def draw(): BigInt = { BigDecimal(r.draw()).toBigInt() }
  }
}

case class AXI4StreamTransaction
(
  data: BigInt = 0,
  last: Boolean = false,
  strb: BigInt = -1,
  keep: BigInt = -1,
  user: BigInt = 0,
  id:   BigInt = 0,
  dest: BigInt = 0
) {
  def randData(dataDist: Rand[BigInt] = Rand.always(0)): AXI4StreamTransaction = {
    copy(data = dataDist.draw())
  }
  def randLast(lastDist: Rand[Boolean] = Rand.always(false)): AXI4StreamTransaction = {
    copy(last = lastDist.draw())
  }
  def randStrb(strbDist: Rand[BigInt] = Rand.always(-1)): AXI4StreamTransaction = {
    copy(strb = strbDist.draw())
  }
  def randKeep(keepDist: Rand[BigInt] = Rand.always(-1)): AXI4StreamTransaction = {
    copy(keep = keepDist.draw())
  }
  def randUser(userDist: Rand[BigInt] = Rand.always(0)): AXI4StreamTransaction = {
    copy(user = userDist.draw())
  }
  def randId(idDist: Rand[BigInt] = Rand.always(0)): AXI4StreamTransaction = {
    copy(id = idDist.draw())
  }
  def randDest(destDist: Rand[BigInt] = Rand.always(0)): AXI4StreamTransaction = {
    copy(dest = destDist.draw())
  }
}

object AXI4StreamTransaction {
  def rand(
           dataDist : Rand[BigInt]  = Rand.always(0),
           lastDist:  Rand[Boolean] = Rand.always(false),
           strbDist:  Rand[BigInt]  = Rand.always(-1),
           keepDist:  Rand[BigInt]  = Rand.always(-1),
           userDist:  Rand[BigInt]  = Rand.always(0),
           idDist:    Rand[BigInt]  = Rand.always(0),
           destDist:  Rand[BigInt]  = Rand.always(0)
           ): AXI4StreamTransaction = {
    AXI4StreamTransaction(
      data = dataDist.draw(),
      last = lastDist.draw(),
      strb = strbDist.draw(),
      keep = keepDist.draw(),
      user = userDist.draw(),
      id   = idDist.draw(),
      dest = destDist.draw()
    )
  }

  def randSeq(
             n: Int,
             dataDist : Rand[BigInt]  = Rand.always(0),
             lastDist:  Rand[Boolean] = Rand.always(false),
             strbDist:  Rand[BigInt]  = Rand.always(-1),
             keepDist:  Rand[BigInt]  = Rand.always(-1),
             userDist:  Rand[BigInt]  = Rand.always(0),
             idDist:    Rand[BigInt]  = Rand.always(0),
             destDist:  Rand[BigInt]  = Rand.always(0)
             ): Seq[AXI4StreamTransaction] = {
    Seq.fill(n) { AXI4StreamTransaction.rand(
      dataDist = dataDist,
      lastDist = lastDist,
      strbDist = strbDist,
      keepDist = keepDist,
      userDist = userDist,
      idDist = idDist,
      destDist = destDist
    )}
  }

  def defaultSeq(n: Int): Seq[AXI4StreamTransaction] = Seq.fill(n)(AXI4StreamTransaction())
  def linearSeq(n: Int): Seq[AXI4StreamTransaction]  = Seq.tabulate(n)(AXI4StreamTransaction(_))
}

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
      if (port.bits.strb.getWidth > 0) {
        if (t.strb == -1) {
          val allOnes = (BigInt(1) << port.bits.strb.getWidth) - 1
          poke(port.bits.strb, allOnes)
        } else {
          poke(port.bits.strb, t.strb)
        }
      }
      if (port.bits.keep.getWidth > 0) {
        if (t.keep == -1) {
          val allOnes = (BigInt(1) << port.bits.keep.getWidth) - 1
          poke(port.bits.keep, allOnes)
        } else {
          poke(port.bits.keep, t.keep)
        }
      }
      if (port.bits.user.getWidth > 0) {
        poke(port.bits.user, t.user)
      }
      if (port.bits.id.getWidth > 0) {
        poke(port.bits.id,   t.id)
      }
      if (port.bits.dest.getWidth > 0) {
        poke(port.bits.dest, t.dest)
      }
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
    value.data.forall(texpect(port.bits.data, _)) &&
      value.last.forall { x => texpect(port.bits.last, if (x) 1 else 0) } &&
      value.strb.forall(texpect(port.bits.strb, _)) &&
      value.keep.forall(texpect(port.bits.keep, _)) &&
      value.user.forall(texpect(port.bits.user, _)) &&
      value.id.forall(texpect(port.bits.id, _)) &&
      value.dest.forall(texpect(port.bits.dest, _))
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

        val data = if (port.params.hasData && port.params.n > 0) {
          peek(port.bits.data)
        } else {
          BigInt(0)
        }
        val last = peek(port.bits.last) != BigInt(0)
        val strb = if (port.params.hasStrb && port.params.n > 0) {
          peek(port.bits.strb)
        } else {
          BigInt(-1)
        }
        val keep = if (port.params.hasKeep && port.params.n > 0) {
          peek(port.bits.keep)
        } else {
          BigInt(-1)
        }
        val user = if (port.params.u > 0) {
          peek(port.bits.user)
        } else {
          BigInt(0)
        }
        val id = if (port.params.i > 0) {
          peek(port.bits.id)
        } else {
          BigInt(0)
        }
        val dest = if (port.params.d > 0) {
          peek(port.bits.dest)
        } else {
          BigInt(0)
        }

        output +:= AXI4StreamTransaction(
          data = data,
          last = last,
          strb = strb,
          keep = keep,
          user = user,
          id   = id,
          dest = dest
        )
      }
    }
  }

  def complete(): Boolean = {
    expects.isEmpty
  }
}

trait AXI4StreamMasterModel extends PeekPokeTester[MultiIOModule] {
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
    for (_ <- 0 until n) {
      stepMasters()
      super.step(1)
    }
  }

  def mastersComplete(): Boolean = {
    masters.map(_.complete()).forall(x => x)
  }

  def stepToCompletion(maxCycles: Int = 1000, silentFail: Boolean = false): Unit = {
    for (_ <- 0 until maxCycles) {
      if (mastersComplete()) {
        step(1)
        return
      } else {
        step(1)
      }
    }
    require(silentFail, s"slavesComplete: ${mastersComplete()}")
  }
}

trait AXI4StreamSlaveModel extends PeekPokeTester[MultiIOModule] {
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
    for (_ <- 0 until n) {
      stepSlaves()
      super.step(1)
    }
  }

  def stepToCompletion(maxCycles: Int = 1000, silentFail: Boolean = false): Unit = {
    for (_ <- 0 until maxCycles) {
      if (slavesComplete()) {
        step(1)
        return
      } else {
        step(1)
      }
    }
    require(silentFail, s"slavesComplete: ${slavesComplete()}")
  }

  def slavesComplete(): Boolean = {
    slaves.map(_.complete()).forall(x => x)
  }
}

trait AXI4StreamModel extends
  AXI4StreamSlaveModel with AXI4StreamMasterModel {

  override def step(n: Int): Unit = {
    for (_ <- 0 until n) {
      stepMasters()
      super[AXI4StreamSlaveModel].step(1)
    }
  }

  override def stepToCompletion(maxCycles: Int = 1000, silentFail: Boolean = false): Unit = {
    for (_ <- 0 until maxCycles) {
      if (slavesComplete() && mastersComplete()) {
        step(1)
        return
      } else {
        step(1)
      }
    }
    require(silentFail, s"stepToCompletion failed at $maxCycles cycles")
  }
}
