// See README.md for license details.

package examples

import org.scalatest.{FlatSpec, Matchers}
import chisel3._
import chisel3.iotesters.TesterOptionsManager
import treadle.DataStorePlugInAnnotation
import treadle.executable.{Big, DataStore, DataStorePlugin, ExecutionEngine, Symbol}

import scala.collection.mutable

class TreadleDataStorePlugInSpec extends FlatSpec with Matchers {
  behavior of "adder circuit on blackbox real"

  it should "allow registers to be declared that infer widths" in {
    case class Extrema(low: BigInt, high: BigInt) {
      def update(value: BigInt): Extrema = {
        if (value < low) { Extrema(value, high) }
        else if (value > high) { Extrema(low, value) }
        else { this }
      }
    }

    class DataCollector {
      val extrema = new mutable.HashMap[String, Extrema]

      def getPlugin(executionEngine: ExecutionEngine): DataStorePlugin = {
        PlugIn(executionEngine)
      }

      case class PlugIn(executionEngine: ExecutionEngine)
        extends DataStorePlugin {
        override def dataStore: DataStore = executionEngine.dataStore

        override def run(symbol: Symbol,
                         offset: Int,
                         previousValue: Big): Unit = {
          extrema(symbol.name) = extrema.get(symbol.name) match {
            case Some(extrema) => extrema.update(dataStore(symbol))
            case None          => Extrema(dataStore(symbol), dataStore(symbol))
          }
        }
      }
    }

    val dataCollector = new DataCollector
    val plugInAnnotation = DataStorePlugInAnnotation("collector", dataCollector.getPlugin)

    val optionsManager = new TesterOptionsManager {
      testerOptions = testerOptions.copy(backendName = "treadle")
      treadleOptions = treadleOptions.copy(extraAnnotations = treadleOptions.extraAnnotations ++ Seq(plugInAnnotation))
    }
    dsptools.Driver.execute(() => new RealAdder, optionsManager) { c =>
      new RealAdderTester(c)
    } should be (true)

    dataCollector.extrema.foreach { extreme =>
      println(extreme)
    }
  }
}
