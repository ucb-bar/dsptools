// See LICENSE for license details.

package ipxact

/**
  * This is a utility class that collects the xml as it is being assembled
  * @param vendor   creator of the content
  * @param library  software being used
  * @param name     circuit name
  * @param version  generator version
  */
class IpxactXmlDocument(vendor: String, library: String, name: String, version: String) {

  var components: Seq[scala.xml.Node] = Seq.empty

  def addComponents(newComponents:  Seq[scala.xml.Node]): Unit = {
    components = components ++ newComponents
  }

  def getXml: scala.xml.Node = {
    <spirit:component xmlns:spirit="http://www.spiritconsortium.org/XMLSchema/SPIRIT/1685-2009"
                      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                      xsi:schemaLocation="http://www.spiritconsortium.org/XMLSchema/SPIRIT/1685-2009">
      <spirit:vendor>{vendor}</spirit:vendor>
      <spirit:library>{library}</spirit:library>
      <spirit:name>{name}</spirit:name>
      <spirit:version>{version}</spirit:version>
      {
      components
      }
    </spirit:component>
  }
}

