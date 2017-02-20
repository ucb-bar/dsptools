package ipxact

import util.GeneratorApp
import org.accellera.spirit.v1685_2009.{File => SpiritFile, Parameters => SpiritParameters, _}
import javax.xml.bind.{JAXBContext, Marshaller}
import java.io.{File, FileOutputStream}
import scala.collection.JavaConverters
import java.util.Collection
import java.math.BigInteger
import rocketchip._
import cde._

trait IPXactGeneratorApp extends GeneratorApp {

  /////////////////////////////////////////////
  //////////// Generate ////////////////////////
  //////////////////////////////////////////////

  def generateIPXact(component: ComponentType): Unit = generateIPXact(Seq(component))
  def generateIPXact(components: Seq[ComponentType]): Unit = {

    val factory = new ObjectFactory

    components.foreach{ componentType => {
      val component = factory.createComponent(componentType)
      // create name based off component parameters
      val of = new File(td, 
        componentType.getLibrary() + ":" + 
        componentType.getName() + ":" + 
        componentType.getVendor() + ":" + 
        componentType.getVersion() + ".xml"
      )
      of.getParentFile().mkdirs()
      val fos = new FileOutputStream(of)
      val context = JAXBContext.newInstance(classOf[ComponentInstance])
      val marshaller = context.createMarshaller()
      marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)
      marshaller.marshal(component, fos)
    }} 
  }
   
}
