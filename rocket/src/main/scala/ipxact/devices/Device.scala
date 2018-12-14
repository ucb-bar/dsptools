// See LICENSE for license details.

package ipxact.devices

trait Device {
  val portMap: Seq[(String, String)]

  /**
    * Find the key that matches the portName with out the prefix.
    * @param prefix    The prefix for this port (usually a bundle name)
    * @param portName  The specific port in bundle
    * @return
    */
  def findLogicalPort(prefix: String, portName: String): Option[String] = {
    portMap.collectFirst { case (key, value) if portName.drop(prefix.length) == key => value }
  }
}
