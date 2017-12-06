package ieee80211

import java.io._
import java.nio.{ByteBuffer, ByteOrder}

import breeze.math.Complex
import breeze.numerics.atan2
// import vegas.{Field, Legend, Line, Nominal, Quant, Vegas}

import scala.collection.mutable.ArrayBuffer


object ADITrace {
  def binary(stream: InputStream): Seq[Complex] = {
    // sometimes, java is dumb
    val buf = new ByteArrayOutputStream()

    var byteRead: Int = 0

    import chisel3.util.is

    var nRead: Int = 0
    var keepGoing: Boolean = true
    val data = new Array[Byte](16384)

    while (keepGoing) {
      nRead = stream.read(data, 0, data.length)
      if (nRead != -1) {
        buf.write(data, 0, nRead)
      } else {
        keepGoing = false
      }
    }

    val bytes = buf.toByteArray()

    val bb = ByteBuffer.wrap(bytes)
    bb.order(ByteOrder.LITTLE_ENDIAN)
    val shorts = new Array[Short]((bytes.length + 1) / 2)
    bb.asShortBuffer().get(shorts)

    shorts.grouped(2).map { case Array(r, i) =>
      Complex(r.toDouble / 32768.0, i.toDouble / 32768.0)
    }.toSeq
  }

  def text(stream: InputStream): Seq[Complex] = {
    scala.io.Source.fromInputStream(stream).getLines().map {
      case "TEXT" => Complex(0, 0)
      case s =>
        val real :: imag :: Nil = s.split("\t").toList
        Complex(real.toDouble / 32768.0, imag.toDouble / 32768.0)
    }.toSeq.tail
  }

  def resourceStream(resource: String): InputStream = {
    getClass.getResourceAsStream(resource)
  }

  def fileStream(name: String): InputStream = {
    new FileInputStream(name)
  }

  def binaryResource(resource: String): Seq[Complex] = {
    binary(resourceStream(resource))
  }
  def binaryFile(name: String): Seq[Complex] = {
    binary(fileStream(name))
  }

  def textResource(resource: String): Seq[Complex] = {
    text(resourceStream(resource))
  }
  def textFile(name: String): Seq[Complex] = {
    text(fileStream(name))
  }
}

object ADITraceMain {
  def main(arg: Array[String]): Unit = {
    val output = ADITrace.binaryResource("/waveforms/wifi-bpsk-loopback-cable.dat")
    val input  = ADITrace.textResource("/waveforms/wifi_bpsk.txt")
    def signalToMap(in: Seq[Complex], name: String) = in.zipWithIndex.flatMap { case (s, idx) =>
      Seq(
        Map(
          "time" -> idx,
          "signal" -> s.real,
          "name" -> (name + " Real")
        ),
        Map(
          "time" -> idx,
          "signal" -> s.imag,
          "name" -> (name + " Imag")
        )
      )
    }

    val cfoSignalMap = signalToMap(output.take(4096 * 2), "Loopback Cable") ++ signalToMap(input.take(4096*2), "Input")

    // Vegas("Sample Multi Series Line Chart", width=1024, height=960)
    //   .withData(cfoSignalMap)
    //   .mark(Line)
    //   .encodeX("time", Quant)
    //   .encodeY("signal", Quant)
    //   .encodeColor(
    //     field="name",
    //     dataType=Nominal,
    //     legend=Legend(orient="left", title="Signal Name"))
    //   .encodeDetailFields(Field(field="name", dataType=Nominal))
    //   .show
  }
}
