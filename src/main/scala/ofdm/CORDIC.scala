package ofdm

import chisel3._
import chisel3.util._
import dsptools.numbers._
import dsptools.numbers.implicits._
import ofdm.CORDICStageSelConfig.{DFunc, MFunc}

import scala.collection.immutable.ListMap

object AddSub {
  def apply[T <: Data : Ring](sel: Bool, a: T, b: T): T = {
    Mux(sel, a - b, a + b)
  }
}

trait CORDICConfigRecord extends Record {}

abstract class CORDICStageSelConfig[T <: Data] {
  def record: CORDICConfigRecord
  def d: DFunc
  def m: MFunc[T]
}

object CORDICStageSelVectoring {
  def apply(xsgn : Bool, ysgn: Bool, zsgn: Bool, record: CORDICConfigRecord) = ysgn
}

object CORDICStageSelRotation {
  def apply(xsgn : Bool, ysgn: Bool, zsgn: Bool, record: CORDICConfigRecord) = !zsgn
}

object CORDICStageSelConfigurable {
  def apply[T <: Data : Ring](xsgn: Bool, ysgn: Bool, zsgn: Bool, record: ConfigurableCORDICConfigRecord): Bool = {
    Mux(
      record.vectoring,
      CORDICStageSelVectoring(xsgn, ysgn, zsgn, record),
      CORDICStageSelRotation (xsgn, ysgn, zsgn, record)
    )
  }
}

class ConfigurableCORDICConfigRecord extends Bundle with CORDICConfigRecord {
  val vectoring = Output(Bool())
  val mode      = Output(UInt(2.W))
}

object ConfigurableCORDICConfigRecord {
  val VECTORING = true.B
  val ROTATION  = false.B

  val CIRCULAR   = 0.U
  val LINEAR     = 1.U
  val HYPERBOLIC = 2.U
}

object CORDICStageMCircular {
  def apply[T <: Data](in: T, config: CORDICConfigRecord): T = in
}

object CORDICStageMLinear {
  def apply[T <: Data : Ring](in: T, config: CORDICConfigRecord): T = Ring[T].zero
}

object CORDICStageMHyperbolic {
  def apply[T <: Data : Ring](in: T, config: CORDICConfigRecord): T = -in
}

object CORDICStageMConfigurable {
  def apply[T <: Data : Ring](in: T, config: ConfigurableCORDICConfigRecord): T = {
    val mode = config.mode
    val isCircular   = mode === ConfigurableCORDICConfigRecord.CIRCULAR
    val isLinear     = mode === ConfigurableCORDICConfigRecord.LINEAR
    val isHyperbolic = mode === ConfigurableCORDICConfigRecord.HYPERBOLIC
    assert(isCircular || isLinear || isHyperbolic, "Invalid mode for CORDIC")
    Mux(isCircular,
      CORDICStageMCircular(in, config),
      Mux(isLinear,
        CORDICStageMLinear(in, config),
        CORDICStageMHyperbolic(in, config)
      )
    )
  }
}

class EmptyCORDICConfigRecord extends CORDICConfigRecord {
  val elements = ListMap[String, Data]()
  def cloneType = (new EmptyCORDICConfigRecord).asInstanceOf[this.type]
}

case class VectoringCORDICStageSelConfig[T <: Data]
(
  m: MFunc[T] = CORDICStageMCircular.apply[T] _
) extends CORDICStageSelConfig[T] {
  val record = new EmptyCORDICConfigRecord
  val d = CORDICStageSelVectoring.apply _
}

case class RotationCORDICStageSelConfig[T <: Data]
(
  m: MFunc[T] = CORDICStageMCircular.apply[T] _
) extends CORDICStageSelConfig[T] {
  val record = new EmptyCORDICConfigRecord
  val d = CORDICStageSelRotation.apply _
}

class ConfigurableCORDICStageSelConfig[T <: Data : Ring] extends CORDICStageSelConfig[T] {
  val m: MFunc[T] = (t: T, config: CORDICConfigRecord) => config match {
    case c: ConfigurableCORDICConfigRecord => CORDICStageMConfigurable.apply[T](t, c)
    case _ => throw new Exception("Invalid record type for configurable CORDIC config, must be ConfigurableCORDICConfigRecord")
  }
  val record = new ConfigurableCORDICConfigRecord
  val d      = (xsgn: Bool, ysgn: Bool, zsgn: Bool, config: CORDICConfigRecord) => config match {
    case c: ConfigurableCORDICConfigRecord => CORDICStageSelConfigurable.apply[T](xsgn, ysgn, zsgn, c)
    case _ => throw new Exception("Invalid record type for configurable CORDIC config, must be ConfigurableCORDICConfigRecord")
  }
}

object CORDICStageSelConfig {
  type DFunc = (Bool, Bool, Bool, CORDICConfigRecord) => Bool
  type MFunc[T] = (T, CORDICConfigRecord) => T

  def CircularVectoringStageSelConfig[T <: Data] = VectoringCORDICStageSelConfig(CORDICStageMCircular.apply[T])
  def CircularRotationStageSelConfig[T <: Data]  = RotationCORDICStageSelConfig(CORDICStageMCircular.apply[T])

  def DivisionStageSelConfig[T <: Data : Ring]       = VectoringCORDICStageSelConfig(CORDICStageMLinear.apply[T])
  def MultiplicationStageSelConfig[T <: Data : Ring] = RotationCORDICStageSelConfig(CORDICStageMLinear.apply[T])

  def HyperbolicVectoringStageSelConfig[T <: Data : Ring] = VectoringCORDICStageSelConfig(CORDICStageMHyperbolic.apply[T])
  def HyperbolicRotationStageSelConfig[T <: Data : Ring]  = RotationCORDICStageSelConfig(CORDICStageMHyperbolic.apply[T])
}

case class CORDICStageParams[T <: Data]
(
  protoX: T,
  protoY: T,
  protoZ: T,
  maxShift: Int,
  config: CORDICStageSelConfig[T]
)

class CORDICStageIO[T <: Data : Ring](params: CORDICStageParams[T]) extends Bundle {
  val xin   = Input(params.protoX)
  val yin   = Input(params.protoY)
  val zin   = Input(params.protoZ)
  val ROMin = Input(params.protoZ)

  val config = Flipped(params.config.record)
  val nShift = Input(UInt(log2Ceil(params.maxShift + 1).W))

  val xout  = Output(params.protoX)
  val yout  = Output(params.protoY)
  val zout  = Output(params.protoZ)
}

class CORDICStage[T <: Data : Ring : Signed : BinaryRepresentation](params: CORDICStageParams[T]) extends Module {
  val io = IO(new CORDICStageIO(params))

  val sgnx = Signed[T].sign(io.xin).neg
  val sgny = Signed[T].sign(io.yin).neg
  val sgnz = Signed[T].sign(io.zin).neg

  val yshift = io.yin >> io.nShift
  val xshift = io.xin >> io.nShift

  val d = params.config.d(sgnx, sgny, sgnz, io.config)
  val my = params.config.m(yshift, io.config)

  // compute x
  io.xout := AddSub(!d, io.xin, my)

  // compute y
  io.yout := AddSub(d, xshift, io.yin)

  // compute z
  io.zout := AddSub(!d, io.ROMin, io.zin)
 }

case class IterativeCORDICParams[T <: Data]
(
  val protoX: T,
  val protoY: T,
  val protoZ: T,
  nStages: Int,
  stagesPerCycle: Int,
  stageConfig: CORDICStageSelConfig[T]
)

class IterativeCORDICChannel[T <: Data](params: IterativeCORDICParams[T]) extends Bundle {
  val x = Output(params.protoX.cloneType)
  val y = Output(params.protoY.cloneType)
  val z = Output(params.protoZ.cloneType)
}

class IterativeCORDICIO[T <: Data](params: IterativeCORDICParams[T]) extends Bundle {
  val in  = Flipped(Decoupled(new IterativeCORDICChannel(params)))
  val out = Decoupled(new IterativeCORDICChannel(params))

  val config = Flipped(params.stageConfig.record)
}

class IterativeCORDIC[T <: Data : Ring : Signed : BinaryRepresentation](params: IterativeCORDICParams[T]) extends Module {
  val io = IO(new IterativeCORDICIO(params))

  val xyzreg = Reg(io.in.bits.cloneType)

  val stageParams = CORDICStageParams(
    protoX = params.protoX,
    protoY = params.protoY,
    protoZ = params.protoZ,
    maxShift = params.nStages - 1,
    config = params.stageConfig
  )

  val stage = Module(new CORDICStage(stageParams))

  stage.io.config <> io.config



}