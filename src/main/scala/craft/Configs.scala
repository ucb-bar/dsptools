package craft

import cde._
import chisel3._
import chisel3.experimental._
import rocketchip._
import uncore.tilelink._
import uncore.coherence.{MICoherence, NullRepresentation}
import uncore.agents.CacheBlockBytes
import junctions._

import dspblocks._
import dspjunctions._
import dsptools.numbers.{Field =>_, _}
import dsptools.numbers.implicits._

object ConfigBuilder {
  def genParams[T <: Data](
    id: String,
    lanesIn_ : Int,                 genInFunc: () => T,
    lanesOut_ : Option[Int] = None, genOutFunc: Option[() => T] = None): Config = new Config(
    (pname, site, here) => pname match {
      case DspBlockId => id
      case GenKey(_id) if _id == id => new GenParameters {
        def genIn[T <: Data] = (genInFunc()).asInstanceOf[T]
        override def genOut[T <: Data] = (genOutFunc.getOrElse(genInFunc))().asInstanceOf[T]
        val lanesIn = lanesIn_
        override val lanesOut = lanesOut_.getOrElse(lanesIn_)
      }
      case _ => throw new CDEMatchError
    })
  def dspBlockParams(
    id: String,
    inputWidth: Int,
    outputWidth: Option[Int] = None): Config = new Config(
      (pname, site, here) => pname match {
        case DspBlockId => id
        case DspBlockKey(_id) if _id == id =>
          DspBlockParameters(
            inputWidth,
            outputWidth.getOrElse(inputWidth)
          )
      })
  def fixedPointBlockParams(id: String, lanesIn: Int, lanesOut: Int, totalWidth: Int, fractionalBits: Int): Config = {
    def getFixedPoint(): FixedPoint = FixedPoint(totalWidth.W, fractionalBits.BP)
    genParams(id, lanesIn, getFixedPoint _, Some(lanesOut), Some(getFixedPoint _))
  }
  def floatingPointBlockParams(id: String, lanesIn: Int, lanesOut: Int, totalWidth: Int, fractionalBits: Int): Config = {
    def getReal(): DspReal = DspReal()
    genParams(id, lanesIn, getReal _, Some(lanesOut), Some(getReal _))
  }
  def fixedPointComplexBlockParams(id: String, lanesIn: Int, lanesOut: Int, totalWidth: Int, fractionalBits: Int): Config = {
    def getFixedPoint(): FixedPoint = FixedPoint(totalWidth.W, fractionalBits.BP)
    def getComplex(): DspComplex[FixedPoint] = DspComplex(getFixedPoint(), getFixedPoint())
    genParams(id, lanesIn, getComplex _, Some(lanesOut), Some(getComplex _))
  }
  def floatingPointComplexBlockParams(id: String, lanesIn: Int, lanesOut: Int, totalWidth: Int, fractionalBits: Int): Config = {
    def getComplex(): DspComplex[DspReal] = DspComplex(DspReal(), DspReal())
    genParams(id, lanesIn, getComplex _, Some(lanesOut), Some(getComplex _))
  }
  def buildDSP(id: String, func: Parameters => DspBlock): Config = new Config(
    (pname, site, here) => pname match {
      case BuildDSPBlock => func
      case BaseAddr(_id) if _id == id => 0
      case _ => throw new CDEMatchError
    }) ++ nastiTLParams(id)
  def nastiTLParams(id: String): Config = new Config(
    (pname, site, here) => pname match {
      case NastiKey => NastiParameters(64, 32, 1)
      case PAddrBits => 32
      case CacheBlockOffsetBits => 6
      case AmoAluOperandBits => 64
      case TLId => id
      case TLKey(_id) if _id == id =>
          TileLinkParameters(
            coherencePolicy = new MICoherence(
              new NullRepresentation(1)),
            nManagers = 1,
            nCachingClients = 0,
            nCachelessClients = 1,
            maxClientXacts = 4,
            maxClientsPerPort = 1,
            maxManagerXacts = 1,
            dataBeats = 4,
            dataBits = 64 * 4)
      case _ => throw new CDEMatchError
    })
}

class WithCraft extends Config(
  (pname, site, here) => pname match {
    case TLKey("XBar") => site(TLKey("MCtoEdge")).copy(
      nCachingClients = 0,
      nCachelessClients = site(OutPorts),
      maxClientXacts = 4,
      maxClientsPerPort = site(InPorts))
    case TLId => "XBar"
    case InPorts => 2
    case OutPorts => site(GlobalAddrMap).flatten.size
    case GlobalAddrMap => {
      val memSize = site(ExtMemSize)
      AddrMap(
        AddrMapEntry(s"chan0", MemSize(memSize - 0x200, MemAttr(AddrMapProt.RWX))),
        AddrMapEntry(s"chan1", MemSize(0x200, MemAttr(AddrMapProt.RWX))))
    }
    case XBarQueueDepth => 2
    case ExtMemSize => 0x800L
    case _ => throw new CDEMatchError
  })

class CraftConfig extends Config(new WithCraft ++ new BaseConfig)
