package ofdm

import breeze.numerics.{atan, atanh, pow}
import chisel3._
import chisel3.core.FixedPoint
import chisel3.util._
import dsptools.SyncROM
import dsptools.numbers._
import ofdm.CORDICStageSelConfig.{DFunc, MFunc, ROMFunc}

import scala.collection.immutable.ListMap

object AddSub {
  def apply[T <: Data : Ring](sel: Bool, a: T, b: T): T = {
    Mux(sel, a + b, a - b)
  }
}

trait CORDICConfigRecord extends Record {}

abstract class CORDICStageSelConfig[T <: Data] {
  def record: CORDICConfigRecord
  def d: DFunc
  def m: MFunc[T]
}

object CORDICStageSelVectoring {
  def apply(xsgn : Bool, ysgn: Bool, zsgn: Bool, record: CORDICConfigRecord) =  ysgn
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
  //                 size, proto =>  addr  config              => value
  type ROMFunc[T] = (Int,  T)    => (UInt, CORDICConfigRecord) => T

  def CircularVectoringStageSelConfig[T <: Data] = VectoringCORDICStageSelConfig(CORDICStageMCircular.apply[T])
  def CircularRotationStageSelConfig[T <: Data]  = RotationCORDICStageSelConfig(CORDICStageMCircular.apply[T])

  def DivisionStageSelConfig[T <: Data : Ring]       = VectoringCORDICStageSelConfig(CORDICStageMLinear.apply[T])
  def MultiplicationStageSelConfig[T <: Data : Ring] = RotationCORDICStageSelConfig(CORDICStageMLinear.apply[T])

  def HyperbolicVectoringStageSelConfig[T <: Data : Ring] = VectoringCORDICStageSelConfig(CORDICStageMHyperbolic.apply[T])
  def HyperbolicRotationStageSelConfig[T <: Data : Ring]  = RotationCORDICStageSelConfig(CORDICStageMHyperbolic.apply[T])
}

object CORDICConstantGeneration {
  def arctan(n: Int): Seq[Double] = linear(n).map { atan(_) }
  def linear(n: Int): Seq[Double] = (0 until n) map { case i =>
      pow(2.0, -i)
  }
  def arctanh(n: Int): Seq[Double] = linear(n).map { atanh(_) }
  def conv[T <: Data : ConvertableTo](in: Double, proto: T): BigInt = {
    ConvertableTo[T].fromDouble(in, proto).litValue()
  }
  def makeCircularROM[T <: Data : ConvertableTo](n: Int, proto: T): (UInt, CORDICConfigRecord) => T = (addr, config) => {
    val table = arctan(n).map(conv(_, proto)) // map(ConvertableTo[T].fromDouble(_, proto).toBigInt())
    val rom = Module(new SyncROM(s"circularTable${n}", table))
    rom.io.addr := addr
    Cat(0.U(1.W), rom.io.data).asTypeOf(proto)
  }
  def makeLinearROM[T <: Data : ConvertableTo : BinaryRepresentation](n: Int, proto: T): (UInt, CORDICConfigRecord) => T = (addr, config) => {
    val unit = ConvertableTo[T].fromDouble(pow(2.0, -n))
    unit << ((n -1).U - addr)
  }
  def makeHyperbolicROM[T <: Data : ConvertableTo](n: Int, proto: T): (UInt, CORDICConfigRecord) => T = (addr, config) => {
    val table = arctanh(n).map(conv(_, proto)) // map(ConvertableTo[T].fromDouble(_, proto).toBigInt())
    val rom = new SyncROM(s"circularTable${n}", table)
    rom.io.addr := addr
    rom.io.data.asTypeOf(proto)
  }
  def makeConfigurableROM[T <: Data : ConvertableTo : BinaryRepresentation](n: Int, proto: T):
  (UInt, CORDICConfigRecord) => T = (addr, config) => {
    val circularROM = makeCircularROM(n, proto)
    val linearROM   = makeLinearROM(n, proto)
    val hyperROM    = makeHyperbolicROM(n, proto)

    val circularValue = circularROM(addr, config)
    val linearValue   = linearROM(addr, config)
    val hyperValue    = hyperROM(addr, config)

    config match {
      case c: ConfigurableCORDICConfigRecord =>
        val mode = c.mode
        val isCircular   = mode === ConfigurableCORDICConfigRecord.CIRCULAR
        val isLinear     = mode === ConfigurableCORDICConfigRecord.LINEAR
        val isHyperbolic = mode === ConfigurableCORDICConfigRecord.HYPERBOLIC
        Mux(isCircular, circularValue, Mux(isLinear, linearValue, hyperValue))
      case _ => throw new Exception("Configurable ROM should have config record of type ConfigurableCORDICConfigRecord")
    }
  }
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

  val config = Input(params.config.record)
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
  io.yout := AddSub(d, io.yin, xshift)

  // compute z
  io.zout := AddSub(!d, io.zin, io.ROMin)
 }

case class IterativeCORDICParams[T <: Data]
(
  val protoX: T,
  val protoY: T,
  val protoZ: T,
  nStages: Int,
  stagesPerCycle: Int,
  stageConfig: CORDICStageSelConfig[T],
  makeROM: ROMFunc[T]
)

class IterativeCORDICChannel[T <: Data](params: IterativeCORDICParams[T]) extends Bundle {
  val x = Output(params.protoX.cloneType)
  val y = Output(params.protoY.cloneType)
  val z = Output(params.protoZ.cloneType)

  override def cloneType = new IterativeCORDICChannel(params).asInstanceOf[this.type]
}

class IterativeCORDICIO[T <: Data](params: IterativeCORDICParams[T]) extends Bundle {
  val in  = Flipped(Decoupled(new IterativeCORDICChannel(params)))
  val out = Decoupled(new IterativeCORDICChannel(params))

  val config = Input(params.stageConfig.record)
}

class IterativeCORDIC[T <: Data : Ring : Signed : BinaryRepresentation](params: IterativeCORDICParams[T])
  extends Module {

  val io = IO(new IterativeCORDICIO(params))


  val STATE_IDLE      = 0
  val STATE_IN_FLIGHT = 1
  val STATE_OUTPUT    = 2

  val state = RegInit(UInt(), STATE_IDLE.U)
  val xyzreg: IterativeCORDICChannel[T] = Reg(io.in.bits.cloneType)
  val configReg = Reg(io.config.cloneType)

  val currentStage = Reg(UInt(log2Ceil(params.nStages).W))
  val romAddr      = Mux(io.in.fire(), 0.U, currentStage + 1.U)


  io.in.ready := (state === STATE_IDLE.U)
  io.out.valid := (state === STATE_OUTPUT.U)

  val stageParams = CORDICStageParams(
    protoX = params.protoX,
    protoY = params.protoY,
    protoZ = params.protoZ,
    maxShift = params.nStages - 1,
    config = params.stageConfig
  )

  val stage = Module(new CORDICStage(stageParams))
  val rom    = params.makeROM(params.nStages, params.protoZ)
  val romOut = rom(romAddr, configReg)

  stage.io.config <> configReg
  stage.io.xin    := xyzreg.x
  stage.io.yin    := xyzreg.y
  stage.io.zin    := xyzreg.z
  stage.io.nShift := currentStage
  stage.io.ROMin  := romOut

  io.out.bits.x  := xyzreg.x
  io.out.bits.y  := xyzreg.y
  io.out.bits.z  := xyzreg.z

  when (io.in.fire()) {
    state        := STATE_IN_FLIGHT.U

    xyzreg       := io.in.bits
    configReg    := io.config
    currentStage := 0.U
  }

  when (state === STATE_IN_FLIGHT.U) {
    xyzreg.x := stage.io.xout
    xyzreg.y := stage.io.yout
    xyzreg.z := stage.io.zout

    currentStage := currentStage + 1.U
    when (currentStage === (params.nStages - 1).U) {
      state := STATE_OUTPUT.U
    }
  }

  when (io.out.fire()) {
    state := STATE_IDLE.U
  }
}

object IterativeCORDIC {
  def circularVectoring[T <: Data : Ring : Signed : BinaryRepresentation : ConvertableTo]
  (protoXY: T, protoZ: T, nStages: Option[Int] = None, stagesPerCycle: Int = 1): IterativeCORDIC[T] = {
    val trueNStages = nStages.getOrElse(protoXY.getWidth)
    val params = IterativeCORDICParams(
      protoX = protoXY,
      protoY = protoXY,
      protoZ = protoZ,
      nStages = trueNStages,
      stagesPerCycle = stagesPerCycle,
      CORDICStageSelConfig.CircularVectoringStageSelConfig[T],
      CORDICConstantGeneration.makeCircularROM[T]
    )
    Module(new IterativeCORDIC(params))
  }

  def circularRotation[T <: Data : Ring : Signed : BinaryRepresentation : ConvertableTo]
  (protoXY: T, protoZ: T, nStages: Option[Int] = None, stagesPerCycle: Int = 1): IterativeCORDIC[T] = {
    val trueNStages = nStages.getOrElse(protoXY.getWidth)
    val params = IterativeCORDICParams(
      protoX = protoXY,
      protoY = protoXY,
      protoZ = protoZ,
      nStages = trueNStages,
      stagesPerCycle = stagesPerCycle,
      CORDICStageSelConfig.CircularRotationStageSelConfig[T],
      CORDICConstantGeneration.makeCircularROM[T]
    )
    Module(new IterativeCORDIC(params))
  }

  def division[T <: Data : Ring : Signed : ConvertableTo : BinaryRepresentation]
  (protoXY: T, protoZ: T, nStages: Option[Int] = None, stagesPerCycle: Int = 1): IterativeCORDIC[T] = {
    val trueNStages = nStages.getOrElse(protoXY.getWidth)
    val params = IterativeCORDICParams(
      protoX = protoXY,
      protoY = protoXY,
      protoZ = protoZ,
      nStages = trueNStages,
      stagesPerCycle = stagesPerCycle,
      CORDICStageSelConfig.DivisionStageSelConfig[T],
      CORDICConstantGeneration.makeLinearROM[T]
    )
    Module(new IterativeCORDIC(params))
  }

  def multiplication[T <: Data : Ring : Signed : ConvertableTo : BinaryRepresentation]
  (protoXY: T, protoZ: T, nStages: Option[Int] = None, stagesPerCycle: Int = 1): IterativeCORDIC[T] = {
    val trueNStages = nStages.getOrElse(protoXY.getWidth)
    val params = IterativeCORDICParams(
      protoX = protoXY,
      protoY = protoXY,
      protoZ = protoZ,
      nStages = trueNStages,
      stagesPerCycle = stagesPerCycle,
      CORDICStageSelConfig.MultiplicationStageSelConfig[T],
      CORDICConstantGeneration.makeLinearROM[T]
    )
    Module(new IterativeCORDIC(params))
  }

  def hyperbolicVectoring[T <: Data : Ring : Signed : ConvertableTo : ConvertableFrom : BinaryRepresentation]
  (protoXY: T, protoZ: T, nStages: Option[Int] = None, stagesPerCycle: Int = 1): IterativeCORDIC[T] = {
    val trueNStages = nStages.getOrElse(protoXY.getWidth)
    val params = IterativeCORDICParams(
      protoX = protoXY,
      protoY = protoXY,
      protoZ = protoZ,
      nStages = trueNStages,
      stagesPerCycle = stagesPerCycle,
      CORDICStageSelConfig.HyperbolicVectoringStageSelConfig[T],
      CORDICConstantGeneration.makeCircularROM[T]
    )
    Module(new IterativeCORDIC(params))
  }

  def hyperbolicRotation[T <: Data : Ring : Signed : ConvertableTo : ConvertableFrom : BinaryRepresentation]
  (protoXY: T, protoZ: T, nStages: Option[Int] = None, stagesPerCycle: Int = 1): IterativeCORDIC[T] = {
    val trueNStages = nStages.getOrElse(protoXY.getWidth)
    val params = IterativeCORDICParams(
      protoX = protoXY,
      protoY = protoXY,
      protoZ = protoZ,
      nStages = trueNStages,
      stagesPerCycle = stagesPerCycle,
      CORDICStageSelConfig.HyperbolicRotationStageSelConfig[T],
      CORDICConstantGeneration.makeCircularROM[T]
    )
    Module(new IterativeCORDIC(params))
  }
}