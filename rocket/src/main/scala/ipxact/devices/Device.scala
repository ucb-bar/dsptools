// See LICENSE for license details.

package ipxact.devices

trait Device {
  def getPortMap(prefix: String): Seq[(String, String)]
}
