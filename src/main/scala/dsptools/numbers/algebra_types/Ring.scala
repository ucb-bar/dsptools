// SPDX-License-Identifier: Apache-2.0

package dsptools.numbers

import chisel3.Data

/* Needs to be redefined from spire */
object Ring {
  def apply[A <: Data](implicit A: Ring[A]): Ring[A] = A
}

trait Ring[A] extends Any with spire.algebra.Ring[A] {
  def plusContext(f: A, g: A): A
  def minusContext(f: A, g: A): A
  def timesContext(f: A, g: A): A
  def negateContext(f: A): A
}
