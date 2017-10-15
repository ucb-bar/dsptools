package ofdm

import chisel3._
import chisel3.experimental.RawModule
import chisel3.util.{Cat, ShiftRegister, Valid, log2Ceil}

class DivisionStageIO(n: Int, useIndexPort: Boolean) extends Bundle {
  val pin = Input(UInt( (2*n).W ))
  val qin = Input(UInt(n.W))

  val d = Input(UInt( (2*n).W ))

  val pout = Output(UInt( (2*n).W ))
  val qout = Output(UInt(n.W))

  val idx  = if (useIndexPort) {
    Some(Input(UInt(log2Ceil(n).W)))
  } else {
    None
  }
}

object SetBit {
  def apply(in: UInt, idx: Int, value: Bool): UInt = {
    val n = in.getWidth
    if (idx == 0) {
      Cat(in(n-1,1), value)
    } else if (idx == n - 1) {
      Cat(value, in(n-2, 0))
    } else {
      Cat(in(n - 1, idx + 1), value, in(idx - 1, 0))
    }
  }
  def apply(in: UInt, idx: UInt, value: Bool): UInt = {
    val mask: UInt = (1.U << idx).asTypeOf(in)
    val out = Wire(UInt())
    when (value) {
      out := in | mask
    }

    when (in(idx) && !value) {
      out := in ^ mask
    }

    out
  }
}

trait HasDivisionIO extends RawModule {
  def io: DivisionStageIO
}

class NonRestoringStage(n: Int, fixedIdx: Option[Int] = None) extends RawModule with HasDivisionIO {
  val useIndexPort = fixedIdx.isEmpty
  val io = IO(new DivisionStageIO(n, useIndexPort))

  val pGtEqZero: Bool = io.pin(2*n-1) === 0.U
  val pShift: UInt = (io.pin << 1).asTypeOf(UInt())

  fixedIdx match {
    case Some(idx) =>
      io.qout := SetBit(io.qin, idx, pGtEqZero)
    case None =>
      io.qout := SetBit(io.qin, io.idx.get, pGtEqZero)
  }


  when (pGtEqZero) {
    io.pout := pShift - io.d
  }   .otherwise {
    io.pout := pShift + io.d
  }
}

class PipelinedDividerInputIO(val n: Int) extends Bundle {
  val num    = UInt(n.W)
  val denom  = UInt(n.W)

  override def cloneType: PipelinedDividerInputIO.this.type = new PipelinedDividerInputIO(n).asInstanceOf[this.type]
}

class PipelinedDividerIO(n: Int) extends Bundle {
  val in = Input(Valid(new PipelinedDividerInputIO(n)))
  val out = Output(Valid(UInt(n.W)))

  override def cloneType: PipelinedDividerIO.this.type = new PipelinedDividerIO(n).asInstanceOf[this.type]
}

class RedundantToNonRedundant(n: Int) extends RawModule {
  val qin  = IO(Input(UInt(n.W)))
  val pin  = IO(Input(UInt((2*n).W)))
  val qout = IO(Output(UInt(n.W)))

  val correction = Mux(pin(2 * n - 1), 1.U, 0.U)

  qout := qin - (~qin).asTypeOf(UInt(n.W)) - correction
}

class PipelinedDivider(val n: Int, val conversionDelay: Int = 1) extends Module {
  require(n > 0)

  val io = IO(new PipelinedDividerIO(n))


  val stages = Seq.tabulate(n + 1) { i: Int => Module(new NonRestoringStage(n + 1, fixedIdx = Some(n-i))) }
  val d: UInt = (io.in.bits.denom << (n + 1)).asTypeOf(UInt()) //UInt((2*n).W))

  val (p, q, _) = stages.foldLeft( (io.in.bits.num, 0.U, d) ) { case ( (p, q, d), stage) =>
    stage.io.d := d
    stage.io.pin := p
    stage.io.qin := q

    (RegNext(stage.io.pout), RegNext(stage.io.qout), RegNext(d))
    //(stage.io.pout, stage.io.qout)
  }

  val nonRedundantConverter = Module(new RedundantToNonRedundant(n + 1))
  nonRedundantConverter.qin := q
  nonRedundantConverter.pin := p

  io.out.bits := ShiftRegister(nonRedundantConverter.qout, conversionDelay)
  io.out.valid := ShiftRegister(io.in.valid, n + 1 + conversionDelay, resetData = false.B, en = true.B)
}
