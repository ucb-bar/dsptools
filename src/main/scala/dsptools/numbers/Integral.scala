// See LICENSE for license details.

package dsptools.numbers

import chisel3.Data

/**
  * Much of this is drawn from non/spire, but using Chisel Bools instead of
  * Java Bools. I suppose a more general solution would be generic in
  * return type, but the use cases there seem obscure.
  */

trait Integer[A<:Data] extends Any with Real[A] with IsIntegral[A]

object Integer {
  @inline final def apply[A<:Data](implicit ev: Integer[A]): Integer[A] = ev
}

class IntegralOps[A<:Data](lhs: A)(implicit ev: Integer[A]) {
  def mod(rhs: A): A = ev.mod(lhs, rhs)
  def %(rhs: A): A = mod(rhs)
  //def factor: prime.Factors = prime.factor(toSafeLong)
  //def isPrime: Boolean = prime.isPrime(toSafeLong)
  //def toSafeLong: SafeLong = SafeLong(ev.toBigInt(lhs))

  //def coerce(a: A): Long = {
  //  val n = ev.toBigInt(a)
  //  if (Long.MinValue <= n && n <= Long.MaxValue) ev.toLong(a)
  // else throw new IllegalArgumentException(s"$lhs too large")
  // }

  //def ! : BigInt = spire.math.fact(coerce(lhs))

  //def choose(rhs: A): BigInt = spire.math.choose(coerce(lhs), coerce(rhs))
}

