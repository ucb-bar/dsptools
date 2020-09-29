// SPDX-License-Identifier: Apache-2.0

package dsptools.numbers

// Make using these ops more like using math.opName
object RealTrig {
  def ln(x: DspReal) = x.ln()
  def log10(x: DspReal) = x.log10()
  def exp(x: DspReal) = x.exp()
  def sqrt(x: DspReal) = x.sqrt()
  def pow(x: DspReal, n: DspReal) = x.pow(n)
  def sin(x: DspReal) = x.sin()
  def cos(x: DspReal) = x.cos()
  def tan(x: DspReal) = x.tan()
  def atan(x: DspReal) = x.atan()
  def asin(x: DspReal) = x.asin()
  def acos(x: DspReal) = x.acos()
  def atan2(y: DspReal, x: DspReal) = y.atan2(x)
  def hypot(x: DspReal, y: DspReal) = x.hypot(y)
  def sinh(x: DspReal) = x.sinh()
  def cosh(x: DspReal) = x.cosh()
  def tanh(x: DspReal) = x.tanh()
  def asinh(x: DspReal) = x.asinh()
  def acosh(x: DspReal) = x.acosh()
  def atanh(x: DspReal) = x.tanh()
}