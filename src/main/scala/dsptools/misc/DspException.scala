// See LICENSE for license details.

package dsptools

import chisel3.ChiselException

case class DspException(message: String) extends ChiselException(message) {
}
