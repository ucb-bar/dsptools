// SPDX-License-Identifier: Apache-2.0

package dsptools

import chisel3.ChiselException

case class DspException(message: String) extends ChiselException(message) {
}
