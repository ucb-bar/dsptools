// See LICENSE for license details.

package dsptools.numbers.resizer

import java.io.PrintWriter

import firrtl.AnnotationMap
import firrtl.annotations.{Annotation, CircuitName}
import logger.{LazyLogging, LogLevel, Logger}

import scala.collection.mutable
import scala.util.matching.Regex

/**
  * Records data gathered by interpreter's instrumentaton
  * @param name          signal name
  * @param firrtlType    string form of firrtl type
  * @param valuesSeen    number of values collected
  * @param min           smallest number seen
  * @param max           largest number seen
  * @param mean          average
  * @param stddev        sigma
  * @param bins          histogram of values
  */
case class BitHistory(
                       name       : String,
                       firrtlType : String,
                       valuesSeen : Long,
                       min        : BigInt,
                       max        : BigInt,
                       mean       : Double,
                       stddev     : Double,
                       bins       : Seq[Long]
                     ) {
  val isUInt: Boolean = firrtlType.startsWith("uint")
  val isSInt: Boolean = firrtlType.startsWith("sint")

  val bitWidth: Int = firrtlType match {
    case BitReducer.TpeWidthRegex(n) => n.toInt
    case _ =>
      //unknown width will be ignored
      0
  }
}

object BitHistory extends LazyLogging {
  def get(fields: Seq[String]): Option[BitHistory] = {
    fields.toList match {
      case name :: tpe :: countString :: minString :: maxString :: meanString :: sigmaString :: binStrings =>
        Some(
          new BitHistory(name, tpe, countString.toLong,
            BigInt(minString), BigInt(maxString),
            meanString.toDouble, sigmaString.toDouble,
            binStrings.map { x => x.toLong }
          )
        )
      case _ =>
        None
    }
  }
}

/**
  * Examines lines (from a interpreter's instrumentation run)
  * and creates annotation to reduce bits where possible.
  * @param lines text lines of csv file
  */
class BitReducer(lines: Seq[String]) extends LazyLogging {
  val annotations = new mutable.ArrayBuffer[Annotation]()
  var bitsRemoved:    Int = 0
  var bitsConsidered: Int = 0

  /**
    * Utility function that computes bits required for a number
    *
    * @param n number of interest
    * @return
    */
  def computeBits(n: BigInt): Int = {
    n.bitLength + (if(n < 0) 1 else 0)
  }

  /**
    * return the smallest number of bits required to hold the given number in
    * an SInt
    * Note: positive numbers will get one minimum width one higher than would be
    * required for a UInt
    *
    * @param num number to find width for
    * @return minimum required bits for an SInt
    */
  def requiredBitsForSInt(num: BigInt): Int = {
    if(num == BigInt(0) || num == -BigInt(1)) {
      1
    }
    else {
      if (num < 0) {
        computeBits(num)
      }
      else {
        computeBits(num) + 1
      }
    }
  }

  def requiredBitsForSInt(low: BigInt, high: BigInt): Int = {
    requiredBitsForSInt(low).max(requiredBitsForSInt(high))
  }

  /**
    * return the smallest number of bits required to hold the given number in
    * an UInt
    * Note: positive numbers will get one minimum width one higher than would be
    * required for a UInt
    *
    * @param num number to find width for
    * @return minimum required bits for an SInt
    */
  def requiredBitsForUInt(num: BigInt): Int = {
    if(num == BigInt(0)) {
      1
    }
    else {
      computeBits(num)
    }
  }

  def createAnnotationIfAppropritate(bitHistory: BitHistory): Boolean = {
    val name   = bitHistory.name
    val width  = bitHistory.bitWidth

    bitsConsidered += width

    if(bitHistory.isUInt) {
      val bitsNeeded = requiredBitsForUInt(bitHistory.max)

      if (bitsNeeded < width) {
        bitsRemoved += (width - bitsNeeded)
        val annotation = Annotation(CircuitName("c"), classOf[ChangeWidthTransform], s"""$name=$bitsNeeded""")
        logger.info(s"Creating annotation ${annotation.value} for $bitHistory")
        annotations += annotation
      }
    }
    else if(bitHistory.isSInt) {
      val neededBits = requiredBitsForSInt(bitHistory.min, bitHistory.max)

      if (neededBits < width) {
        bitsRemoved += (width - neededBits)

        val annotation = Annotation(CircuitName("c"), classOf[ChangeWidthTransform], s"""$name=$neededBits""")
        logger.info(s"Creating annotation ${annotation.value} for $bitHistory")
        annotations += annotation
      }
    }
    true
  }

  def getAnnotationMap: AnnotationMap = {
    AnnotationMap(annotations)
  }

  //noinspection ScalaStyle
  def writeAnnotations(fileName: String): Unit = {
    val writer = new PrintWriter(new java.io.File(fileName))
    annotations.foreach { annotation =>
      writer.println(annotation.serialize)
    }
    writer.close()
  }

  def getReportString: String = {
    val reduction: Double = {
      if(bitsConsidered == 0) {
        0.0
      }
      else {
        (bitsRemoved.toDouble / bitsConsidered.toDouble ) * 100.0
      }
    }
    f"""
      |Bit Reducer:
      |Change annotations created   ${annotations.length}%10d
      |Total bits removed           $bitsRemoved%10d
      |Total bits considered        $bitsConsidered%10d
      |Percentage                   $reduction%10.4f %%
    """.stripMargin
  }

  def run() {
    lines.zipWithIndex.foreach { case (s, lineNumber) =>
      val fields = s.split(",").map(_.trim)
      logger.debug(s"reading line $lineNumber : $s")
      BitHistory.get(fields) match {
        case Some(record) =>
          createAnnotationIfAppropritate(record)
        case _ =>
          if(s.nonEmpty) {
            logger.warn(s"Skipping bad input line: $s")
          }
      }

    }
  }
}

object BitReducer extends LazyLogging {
  val TpeWidthRegex: Regex  = """^[us]int<([-\d]+)>$""".r

  def main(args: Array[String]): Unit = {
    Logger.setLevel(LogLevel.Info)

    if(args.nonEmpty) {
      logger.debug(s"args ${args.mkString(", ")}")
      val file = new java.io.File(args.head)
      if(file.exists()) {
        // drop is just hack to get rid of header line
        val data = io.Source.fromFile(file).getLines().toList.drop(2)

        val im = new BitReducer(data)
        im.run()
        //noinspection ScalaStyle
        println(im.getReportString)
      }
    }
  }
}
