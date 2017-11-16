// See LICENSE for license details.

package dsptools.numbers.resizer

import dsptools.misc.BitWidth._

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

  def bigInt(d: Double): BigInt = {
    BigDecimal(d).setScale(0, BigDecimal.RoundingMode.HALF_UP).toBigInt
  }

  def minBySigma(sigmaNumber: Double): BigInt = {
    if(sigmaNumber <= 0.0) {
      this.min
    }
    else {
      bigInt(mean - (stddev * sigmaNumber)).max(this.min)
    }
  }

  def maxBySigma(sigmaNumber: Double): BigInt = {
    if(sigmaNumber <= 0.0) {
      this.max
    }
    else {
      bigInt(mean + (stddev * sigmaNumber)).min(this.max)
    }
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
class BitReducer(
                  lines: Seq[String],
                  trimBySigma: Double = 0.0,
                  fudgeConstant: Int = 0,
                  htmlReportFileName: String = ""
                ) extends LazyLogging {
  val annotations = new mutable.ArrayBuffer[Annotation]()
  var bitsRemoved:    Int = 0
  var bitsConsidered: Int = 0

  val htmlBuffer = new mutable.StringBuilder()
  var htmlLines = 0

  def td(s: String): String = s"<td>$s</td>\n"
  def th(s: String): String = s"<th>$s</th>\n"
  def tr(s: String): String = s"<tr>\n$s</tr>\n"

  //scalastyle:off method.length
  def startHtml(): Unit = {
    htmlBuffer ++=
      """<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN"
        |        "http://www.w3.org/TR/html4/strict.dtd">
        |<head>
        |
        |    <!-- script type="text/javascript" src="jquery-3.2.1.js"></script -->
        |    <!-- script type="text/javascript" src="jquery.sparkline.js"></script -->
        |
        |    <script
        |      src="https://code.jquery.com/jquery-3.2.1.min.js"
        |      integrity="sha256-hwg4gsxgFZhOsEEamdOYGBf13FyQuiTwlAQgxVSNgt4="
        |      crossorigin="anonymous">
        |    </script>
        |
        |    <script
        |     src="https://cdnjs.cloudflare.com/ajax/libs/jquery-sparklines/2.1.2/jquery.sparkline.js">
        |     </script>
        |
        |    <script
        |      src="https://cdnjs.cloudflare.com/ajax/libs/jquery.tablesorter/2.29.0/js/jquery.tablesorter.min.js">
        |    </script>
        |
        |    <script type="text/javascript">
        |        $(function() {
        |            /** This code runs when everything has been loaded on the page */
        |            /* Inline sparklines take their values from the contents of the tag */
        |            $('.inlinesparkline').sparkline();
        |
        |            /* Sparklines can also take their values from the first argument
        |            passed to the sparkline() function */
        |            var myvalues = [10,8,5,7,4,4,1];
        |            $('.dynamicsparkline').sparkline(myvalues);
        |
        |            /* The second argument gives options such as chart type */
        |            $('.dynamicbar').sparkline(myvalues, {type: 'bar', barColor: 'green'} );
        |
        |            /* Use 'html' instead of an array of values to pass options
        |            to a sparkline with data in the tag */
        |            $('.inlinebar').sparkline('html', {type: 'bar', barColor: 'red'} );
        |        });
        |    </script>
        |
        |    <script type="text/javascript"
        |     src="https://cdnjs.cloudflare.com/ajax/libs/jquery.tablesorter/2.29.0/js/jquery.tablesorter.min.js">
        |    </script>
        |
        |    <script>
        |    $(document).ready(function()
        |        {
        |            $("#myTable").tablesorter();
        |        }
        |    );
        |    </script>
        |
        |    <style>
        |    table {
        |    font-family: arial, sans-serif;
        |    border-collapse: collapse;
        |    width: 100%;
        |    }
        |    td,th {
        |        border: 1px solid #dddddd;
        |        text-align: left;
        |        padding: 8px;
        |    }
        |    tr:nth-child(even) {
        |        background-color: #dddddd;
        |    }
        |
        |</style>
        |</head>
        |<body>
        |<table id="myTable" class="tablesorter" style="width:100%">
        |<thead>
      """.stripMargin
  }
//  <link rel="stylesheet" type="text/css" href="bitreduce.css">

  def finishHtml(): Unit = {
    htmlBuffer ++=
      """
        |</tbody>
        |</table>
        |</body>
        |</html>
        |
      """.stripMargin

    val writer = new PrintWriter(new java.io.File(htmlReportFileName))
    //scalastyle:off regex
    writer.println(htmlBuffer.toString)
    writer.close()
    println(s"Writing html as $htmlReportFileName")
  }

  def buildHeader(): Unit = {
    val headerCells =
      th("#") +
      th(s"Name") +
      th("Type") +
      th("Reduce by max") +
      th("Reduce σ = 2") +
      th("Reduce σ = 3") +
      th("Reduce σ = 4") +
      th("Samples") +
      th("Min") +
      th("Max") +
      th("Mean") +
      th("σ") +
      th("Distribution")

    htmlBuffer ++= tr(headerCells)
    htmlBuffer ++= "</thead>\n<tbody>\n"
  }

  def buildHtml(bitHistory: BitHistory): Unit = {
    def showReduce(trimBySigma: Double): String = {
      val bitsToCut = if(bitHistory.isUInt) {
        bitHistory.bitWidth - requiredBitsForUInt(bitHistory.maxBySigma(trimBySigma))
      }
      else {
        bitHistory.bitWidth - requiredBitsForUInt(bitHistory.maxBySigma(trimBySigma))
      }
      if(bitsToCut <= 0) "-" else bitsToCut.toString
    }

    if(bitHistory.bitWidth < 2) return
    if(bitHistory.name.contains("._T")) return

    htmlLines += 1

//    if(htmlReportFileName.nonEmpty) {
      val cells =
        td(htmlLines.toString) +
        td(bitHistory.name) +
        td(bitHistory.firrtlType.take(1).toUpperCase() + s"Int<${bitHistory.bitWidth}>") +
        td(showReduce(0.0)) +
        td(showReduce(2.0)) +
        td(showReduce(3.0)) +
        td(showReduce(4.0)) +
        td(bitHistory.valuesSeen.toString) +
        td(bitHistory.min.toString()) +
        td(bitHistory.max.toString()) +
        td(f"${bitHistory.mean}%.4f") +
        td(f"${bitHistory.stddev}%.4f") +
        td(s"""<span class="inlinebar">${bitHistory.bins.mkString(",")}</span>""")
      val row = tr(cells)
      htmlBuffer ++= row
//    }
  }



  def createAnnotationIfAppropriate(bitHistory: BitHistory): Boolean = {
    val name   = bitHistory.name
    val width  = bitHistory.bitWidth

    bitsConsidered += width

    buildHtml(bitHistory)

    //TODO (chick), it would be nice to remove this but currently adding the io.X_reduced breaks things
    if(bitHistory.name.contains(".io_")) {
      return true
    }

    if(bitHistory.isUInt) {
      val bitsNeeded = (requiredBitsForUInt(bitHistory.maxBySigma(trimBySigma)) + fudgeConstant).max(2)

      if (bitsNeeded < width) {
        bitsRemoved += (width - bitsNeeded)
        val annotation = Annotation(CircuitName("c"), classOf[ChangeWidthTransform], s"""$name=$bitsNeeded""")
        logger.debug(s"Creating annotation ${annotation.value} for $bitHistory")

        annotations += annotation
      }
    }
    else if(bitHistory.isSInt) {
      val neededBits = (requiredBitsForSInt(bitHistory.minBySigma(trimBySigma), bitHistory.maxBySigma(trimBySigma)) +
                         fudgeConstant).max(4)

      if (neededBits < width) {
        bitsRemoved += (width - neededBits)

        val annotation = Annotation(CircuitName("c"), classOf[ChangeWidthTransform], s"""$name=$neededBits""")
        // logger.debug(s"Creating annotation ${annotation.value} for $bitHistory")
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
    startHtml()
    buildHeader()
    lines.zipWithIndex.foreach { case (s, lineNumber) =>
      val fields = s.split(",").map(_.trim)
      logger.debug(s"reading line $lineNumber : $s")
      BitHistory.get(fields) match {
        case Some(record) =>
          createAnnotationIfAppropriate(record)
        case _ =>
          if(s.nonEmpty) {
            logger.warn(s"Skipping bad input line: $s")
          }
      }
    }
    finishHtml()
  }
}

//scalastyle:off magic.number
object BitReducer extends LazyLogging {
  val TpeWidthRegex: Regex  = """^[us]int<([-\d]+)>$""".r

  def main(args: Array[String]): Unit = {
    Logger.setLevel(LogLevel.Info)

    if(args.nonEmpty) {
      logger.debug(s"args ${args.mkString(", ")}")
      val file = new java.io.File(args.head)
      if(file.exists()) {
        val root = if(file.getName.endsWith(".csv")) file.getName.dropRight(4) else file.getName
        val htmlFileName = file.getParent + "/" + root + ".html"

        // drop is just hack to get rid of header line
        val data = io.Source.fromFile(file).getLines().toList.drop(2)

        val bitReducer = new BitReducer(data, htmlReportFileName = htmlFileName)
        bitReducer.run()
        //noinspection ScalaStyle
        println(bitReducer.getReportString)
      }
    }
  }
}
