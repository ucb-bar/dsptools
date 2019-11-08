package dspblocks

import chisel3._
import chisel3.internal.requireIsChiselType
import chisel3.util.log2Ceil
import freechips.rocketchip.regmapper._

object MemToRegmap {
  def nextPowerOfTwo(width: Int): Int = {
    1 << log2Ceil(width)
  }

  /**
    * Make a Regmap wrapper for a @@SyncReadMem[T]
    * @param proto The prototype used to make @mem
    * @param mem The memory to map with a regmap
    * @param baseAddress
    * @tparam T
    * @return
    */
  def apply[T <: Data](
                        proto: T, mem: SyncReadMem[T],
                        memReadReady: Bool = true.B, memWriteReady: Bool = true.B,
                        baseAddress: Int = 0, wordSizeBytes: Option[Int] = None
                      ): (Seq[RegField.Map], Bool, Bool) = {
    requireIsChiselType(proto)
    val protoWidth = proto.getWidth
    val memWidth   = nextPowerOfTwo(protoWidth)
    val bytesPerMemEntry = wordSizeBytes.getOrElse(log2Ceil(memWidth) - 2)

    val readIdx = WireInit(UInt(), 0.U)
    val readEn  = WireInit(false.B)
    val readData = mem.read(readIdx) //, memReadReady)

    val writeIdx = WireInit(UInt(), 0.U)
    val writeEn  = WireInit(false.B)
    val writeData: T = WireInit(proto, 0.U.asTypeOf(proto))

    when (writeEn && memWriteReady) {
      mem.write(writeIdx, writeData)
    }

    val map: Seq[RegField.Map] = (0 until mem.length.toInt).map (i => {
      (baseAddress + bytesPerMemEntry * i) ->
        Seq(RegField(
          /*memWidth*/ bytesPerMemEntry * 8,
          RegReadFn((ivalid, oready) => {
            readEn := ivalid
            when (ivalid && oready) {
              readIdx := i.U
            }
            (memReadReady, RegNext(ivalid), readData.asUInt)
          }),
          RegWriteFn((ivalid, oready, data) => {
            (memWriteReady, RegNext(ivalid))
          })
        ))

    })

    (map, readEn, writeEn)
  }
}