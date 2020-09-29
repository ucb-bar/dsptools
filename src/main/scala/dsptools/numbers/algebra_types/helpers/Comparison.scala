// SPDX-License-Identifier: Apache-2.0

package dsptools.numbers

import chisel3.{Bool, Bundle, Wire}
import chisel3.util.{Valid, ValidIO}

// Helper bundles that theoretically aren't needed unless you wan't to be super general
class ComparisonBundle extends Bundle {
  val eq = Bool()
  // ignore lt if eq is true
  val lt = Bool()
}

// Note: Only useful with Partial Order (when comparisons might not be valid)
object ComparisonHelper {
  def apply(valid: Bool, eq: Bool, lt: Bool): ValidIO[ComparisonBundle] = {
    val ret = Wire(Valid(new ComparisonBundle().cloneType))
    ret.bits.eq := eq
    ret.bits.lt := lt
    ret.valid := valid
    ret
  }
  def apply(eq: Bool, lt: Bool): ComparisonBundle = {
    val ret = Wire(new ComparisonBundle().cloneType)
    ret.eq := eq
    ret.lt := lt
    ret
  }
}
