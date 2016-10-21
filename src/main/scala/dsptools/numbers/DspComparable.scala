// See LICENSE for license details.

package dsptools.numbers

import scala.util.DynamicVariable
import chisel3.util.{Valid, ValidIO}
import chisel3.{Bool, Bundle, Data, Mux, UInt}
import dsptools.{TrimType, OverflowType, DspContext}
import spire.macros.Ops
import spire.math.Algebraic
import spire.std.{IntIsEuclideanRing, IntIsReal}

import scala.collection.mutable
import scala.language.experimental.macros

import scala.language.implicitConversions

/**
  * Created by rigge on 9/21/16.
  *
  * Much of this is drawn from non/spire, but using Chisel Bools instead of
  * Java Bools. I suppose a more general solution would be generic in
  * return type, but the use cases there seem obscure.
  */

/* Eq.scala */
/**
  * A type class used to determine equality between 2 instances of the same
  * type. Any 2 instances `x` and `y` are equal if `eqv(x, y)` is `true`.
  * Moreover, `eqv` should form an equivalence relation.
  */
trait Eq[A <: Data] extends Any {
  /** Returns `true` if `x` and `y` are equivalent, `false` otherwise. */
  def eqv(x:A, y:A): Bool

  /** Returns `false` if `x` and `y` are equivalent, `true` otherwise. */
  def neqv(x:A, y:A): Bool = !eqv(x, y)

  /**
    * Constructs a new `Eq` instance for type `B` where 2 elements are
    * equivalent iff `eqv(f(x), f(y))`.
    */
  def on[B <: Data](f:B => A): Eq[B] = new MappedEq(this)(f)
}

private[numbers] class MappedEq[A <: Data, B <: Data](eq: Eq[B])(f: A => B) extends Eq[A] {
  def eqv(x: A, y: A): Bool = eq.eqv(f(x), f(y))
}

object Eq {
  def apply[A <: Data](implicit e:Eq[A]):Eq[A] = e

  def by[A <: Data, B <: Data](f:A => B)(implicit e:Eq[B]): Eq[A] = new MappedEq(e)(f)
}


class ComparisonBundle extends Bundle {
  val eq = Bool()
  // ignore lt if eq is true
  val lt = Bool()
}

class SignBundle extends Bundle {
  val zero = Bool()
  // ignore neg if zero is true
  val neg  = Bool()
}

object ComparisonHelper {
  def apply(valid: Bool, eq: Bool, lt: Bool): ValidIO[ComparisonBundle] = {
    val ret = Valid(new ComparisonBundle())
    ret.bits.eq := eq
    ret.bits.lt := lt
    ret.valid := valid
    ret
  }
  def apply(eq: Bool, lt: Bool): ComparisonBundle = {
    val ret = new ComparisonBundle()
    ret.eq := eq
    ret.lt := lt
    ret
  }
}


/* PartialOrder.scala */
/**
  * The `PartialOrder` type class is used to define a partial ordering on some type `A`.
  *
  * A partial order is defined by a relation <=, which obeys the following laws:
  *
  * - x <= x (reflexivity)
  * - if x <= y and y <= x, then x === y (anti-symmetry)
  * - if x <= y and y <= z, then x <= z (transitivity)
  *
  * To compute both <= and >= at the same time, we use a Double number
  * to encode the result of the comparisons x <= y and x >= y.
  * The truth table is defined as follows:
  *
  * x <= y    x >= y      Double
  * true      true        = 0.0     (corresponds to x === y)
  * false     false       = NaN     (x and y cannot be compared)
  * true      false       = -1.0    (corresponds to x < y)
  * false     true        = 1.0     (corresponds to x > y)
  *
  */
trait PartialOrder[A <: Data] extends Any with Eq[A] {
  self =>
  /** Result of comparing `x` with `y`. Returns ValidIO[ComparisonBundle]
    *  with `valid` false if operands are not comparable. If operands are
    * comparable, `bits.lt` will be true if `x` < `y` and `bits.eq` will
    * be true if `x` = `y``
    */
  def partialCompare(x: A, y: A): ValidIO[ComparisonBundle]
  /** Result of comparing `x` with `y`. Returns None if operands
    * are not comparable. If operands are comparable, returns Some[Int]
    * where the Int sign is:
    * - negative iff `x < y`
    * - zero     iff `x == y`
    * - positive iff `x > y`
    */

  /** Returns Some(x) if x <= y, Some(y) if x > y, otherwise None. */
  def pmin(x: A, y: A): ValidIO[A] = {
    val c = partialCompare(x, y)
    val value = Mux(c.bits.lt, x, y)
    val ret = Valid(value)
    ret.valid := c.valid
    ret
  }

  /** Returns Some(x) if x >= y, Some(y) if x < y, otherwise None. */
  def pmax(x: A, y: A): ValidIO[A] = {
    val c = partialCompare(x, y)
    val value = Mux(!c.bits.lt, x, y)
    val ret = Valid(value)
    ret.valid := c.valid
    ret
  }

  // The following should be overriden in priority for performance
  def eqv(x: A, y: A): Bool = {
    val c = partialCompare(x, y)
    c.bits.eq && c.valid
  }
  def lteqv(x: A, y: A): Bool = {
    val c = partialCompare(x, y)
    (c.bits.lt || c.bits.eq) && c.valid
  }
  def lt(x: A, y: A): Bool = {
    val c = partialCompare(x, y)
    c.bits.lt && c.valid
  }

  def gteqv(x: A, y: A): Bool = lteqv(y, x)
  def gt(x: A, y: A): Bool = lt(y, x)

  /**
    * Defines a partial order on `B` by mapping `B` to `A` using `f` and using `A`s
    * order to order `B`.
    */
  override def on[B <: Data](f: B => A): PartialOrder[B] = new MappedPartialOrder(this)(f)

  /**
    * Defines a partial order on `A` where all arrows switch direction.
    */
  def reverse: PartialOrder[A] = new ReversedPartialOrder(this)
}

private[numbers] class MappedPartialOrder[A <: Data, B <: Data](partialOrder: PartialOrder[B])(f: A => B) extends PartialOrder[A] {
  def partialCompare(x: A, y: A): ValidIO[ComparisonBundle] = partialOrder.partialCompare(f(x), f(y))
}

private[numbers] class ReversedPartialOrder[A <: Data](partialOrder: PartialOrder[A]) extends PartialOrder[A] {
  def partialCompare(x: A, y: A): ValidIO[ComparisonBundle] = partialOrder.partialCompare(y, x)
}

object PartialOrder {
  @inline final def apply[A <: Data](implicit po: PartialOrder[A]): PartialOrder[A] = po

  def by[A <: Data, B <: Data](f: A => B)(implicit po: PartialOrder[B]): PartialOrder[A] = po.on(f)

  def from[A <: Data](f: (A, A) => ValidIO[ComparisonBundle]): PartialOrder[A] = new PartialOrder[A] {
    def partialCompare(x: A, y: A): ValidIO[ComparisonBundle] = f(x, y)
  }

/*  implicit def partialOrdering[A <: Data](implicit po: PartialOrder[A]): PartialOrdering[A] = new PartialOrdering[A] {
    def partialCompare(x: A, y: A): ValidIO[ComparisonBundle] = po.partialCompare(x, y)
    def lteq(x: A, y: A): Bool = po.lteqv(x, y)
  }
  */
}

/*
private[numbers] class DerivedPartialOrdering[A <: Data](partialOrder: PartialOrder[A]) extends PartialOrdering[A] {
  def partialCompare(x: A, y: A): ValidIO[ComparisonBundle] = partialOrder.partialCompare(x, y)
  def lteq(x: A, y: A): Bool = partialOrder.lteqv(x, y)
}
*/

/* Order.scala */
/**
  * The `Order` type class is used to define a total ordering on some type `A`.
  * An order is defined by a relation <=, which obeys the following laws:
  *
  * - either x <= y or y <= x (totality)
  * - if x <= y and y <= x, then x == y (antisymmetry)
  * - if x <= y and y <= z, then x <= z (transitivity)
  *
  * The truth table for compare is defined as follows:
  *
  * x <= y    x >= y      Int
  * true      true        = 0     (corresponds to x == y)
  * true      false       < 0     (corresponds to x < y)
  * false     true        > 0     (corresponds to x > y)
  *
  * By the totality law, x <= y and y <= x cannot be both false.
  */
trait Order[A <: Data] extends Any with PartialOrder[A] {
  self =>

  def partialCompare(x: A, y: A): ValidIO[ComparisonBundle] = {
    val c = compare(x, y)
    ComparisonHelper(Bool(true), c.eq, c.lt)
  }

  override def eqv(x: A, y: A): Bool = compare(x, y).eq
  override def gt(x: A, y: A): Bool = {
    val c = compare(x, y)
    !(c.eq || c.lt)
  }
  override def lt(x: A, y: A): Bool = compare(x, y).lt
  override def gteqv(x: A, y: A): Bool = {
    val c = compare(x, y)
    c.eq || (!c.lt)
  }
  override def lteqv(x: A, y: A): Bool = {
    val c = compare(x, y)
    c.lt || c.eq
  }

  def min(x: A, y: A): A = Mux(lt(x, y), x, y)
  def max(x: A, y: A): A = Mux(gt(x, y), x, y)
  def compare(x: A, y: A): ComparisonBundle

  /**
    * Defines an order on `B` by mapping `B` to `A` using `f` and using `A`s
    * order to order `B`.
    */
  override def on[B <: Data](f: B => A): Order[B] = new MappedOrder(this)(f)

  /**
    * Defines an ordering on `A` where all arrows switch direction.
    */
  override def reverse: Order[A] = new ReversedOrder(this)
}

private[numbers] class MappedOrder[A <: Data, B <: Data](order: Order[B])(f: A => B) extends Order[A] {
  def compare(x: A, y: A): ComparisonBundle = order.compare(f(x), f(y))
}

private[numbers] class ReversedOrder[A <: Data](order: Order[A]) extends Order[A] {
  def compare(x: A, y: A): ComparisonBundle = order.compare(y, x)
}

object Order {
  @inline final def apply[A <: Data](implicit o: Order[A]): Order[A] = o

  def by[A <: Data, B <: Data](f: A => B)(implicit o: Order[B]): Order[A] = o.on(f)

  def from[A <: Data](f: (A, A) => ComparisonBundle): Order[A] = new Order[A] {
    def compare(x: A, y: A): ComparisonBundle = f(x, y)
  }

  /*implicit def ordering[A <: Data](implicit o: Order[A]): Ordering[A] = new Ordering[A] {
    def compare(x: A, y: A): ComparisonBundle = o.compare(x, y)
  }
  */
}

/* Sign.scala */
/**
  * A simple ADT representing the `Sign` of an object.
  */
sealed class Sign(that: SignBundle) extends SignBundle {
  import Sign._

  zero := that.zero
  neg  := that.neg

  def unary_-(): Sign = new Sign(Sign(this.zero, !this.neg))

  def *(that: Sign): Sign = new Sign(Sign(
    this.zero || that.zero,
    this.neg ^ that.neg
  ))

  def **(that: Int): Sign = **(UInt(that))
  def **(that: UInt): Sign = new Sign(Sign(this.zero, this.neg ^ that(0)))
}

object Sign {
  case object Zero extends Sign(Sign(Bool(true), Bool(false)))
  case object Positive extends Sign(Sign(Bool(false), Bool(false)))
  case object Negative extends Sign(Sign(Bool(false), Bool(true)))

  def apply(zero: Bool, neg: Bool): SignBundle = {
    val bundle = new SignBundle
    bundle.zero := zero
    bundle.neg  := neg
    bundle
  }

  implicit def apply(i: Int): Sign =
    if (i == 0) Zero else if (i > 0) Positive else Negative

  implicit def apply(i: ComparisonBundle): Sign = {
    val bundle = new SignBundle
    bundle.zero := i.eq
    bundle.neg := i.lt
    new Sign(bundle)
  }

  class SignAlgebra extends CMonoid[Sign] with Signed[Sign] with Order[Sign] {
    def id: Sign = Positive
    def op(a: Sign, b: Sign): Sign = a * b

    override def sign(a: Sign): Sign = a
    def signum(a: Sign): ComparisonBundle = ComparisonHelper(a.zero, a.neg)
    def abs(a: Sign): Sign = if (a == Negative) Positive else a

    def compare(x: Sign, y: Sign): ComparisonBundle = {
      val eq = Mux(x.zero,
        // if x is zero, y must also be zero for equality
        y.zero,
        // if x is not zero, y must not be zero and must have the same sign
        !y.zero && (x.neg === y.neg)
      )
      // lt only needs to be correct when eq not true
      val lt = Mux(x.zero,
        // if x is zero, then true when y positive
        !y.zero && !y.neg,
        // if x is not zero, then true when x is negative and y not negative
        x.neg && (y.zero || !y.neg)
      )

      ComparisonHelper(eq, lt)
    }
  }

  implicit final val SignAlgebra = new SignAlgebra

  implicit final val SignMultiplicativeGroup: MultiplicativeCMonoid[Sign] =
    Multiplicative(SignAlgebra)

  implicit def SignAction[A<:Data](implicit A: AdditiveGroup[A]): MultiplicativeAction[A, Sign] =
    new MultiplicativeAction[A, Sign] {
      def gtimesl(s: Sign, a: A): A = Mux(s.zero,
        A.zero,
        Mux(s.neg, A.negate(a), a )
      )
      def gtimesr(a: A, s: Sign): A = gtimesl(s, a)
    }
}

/* Signed.scala */
/**
  * A trait for things that have some notion of sign and the ability to ensure
  * something has a positive sign.
  */
trait Signed[A] extends Any {
  /** Returns Zero if `a` is 0, Positive if `a` is positive, and Negative is `a` is negative. */
  def sign(a: A): Sign = Sign(signum(a))

  /** Returns 0 if `a` is 0, > 0 if `a` is positive, and < 0 is `a` is negative. */
  def signum(a: A): ComparisonBundle

  /** An idempotent function that ensures an object has a non-negative sign. */
  def abs(a: A): A

  def isSignZero(a: A): Bool = signum(a).eq
  def isSignPositive(a: A): Bool = {
    val s = signum(a)
    !s.eq && !s.lt
  }
  def isSignNegative(a: A): Bool = {
    val s = signum(a)
    !s.eq && s.lt
  }

  def isSignNonZero(a: A): Bool = !signum(a).eq
  def isSignNonPositive(a: A): Bool = signum(a).lt
  def isSignNonNegative(a: A): Bool = !signum(a).lt
}

object Signed {
  implicit def orderedRingIsSigned[A<:Data: Order: Ring]: Signed[A] = new OrderedRingIsSigned[A]

  def apply[A<:Data](implicit s: Signed[A]): Signed[A] = s
}

private class OrderedRingIsSigned[A<:Data](implicit o: Order[A], r: Ring[A]) extends Signed[A] {
  def signum(a: A): ComparisonBundle = o.compare(a, r.zero)
  def abs(a: A): A = Mux(signum(a).lt, r.negate(a), a)
}

/* IsReal.scala */
/**
  * A simple type class for numeric types that are a subset of the reals.
  */
trait IsReal[A<:Data] extends Any with Order[A] with Signed[A] {

  /**
    * Rounds `a` the nearest integer that is greater than or equal to `a`.
    */
  def ceil(a: A): A

  /**
    * Rounds `a` the nearest integer that is less than or equal to `a`.
    */
  def floor(a: A): A

  /**
    * Rounds `a` to the nearest integer.
    */
  def round(a: A): A

  /**
    * Returns `true` iff `a` is a an integer.
    */
  def isWhole(a: A): Bool

  /**
    * Approximates `a` as a `Double`.
    */
  def toDouble(a: A): DspReal
}

object IsReal {
  def apply[A<:Data](implicit A: IsReal[A]): IsReal[A] = A
}

trait IsAlgebraic[A<:Data] extends Any with IsReal[A] {
  //def toAlgebraic(a: A): Algebraic
  //def toDouble(a: A): DspReal = DspReal(toAlgebraic(a).toDouble) // ???
}
object IsAlgebraic {
  def apply[A<:Data](implicit A: IsAlgebraic[A]): IsAlgebraic[A] = A
}

trait IsRational[A<:Data] extends Any with IsAlgebraic[A] {
  //def toRational(a: A): Rational
  //def toAlgebraic(a: A): Algebraic = ???// Algebraic(toRational(a))
}

object IsRational {
  def apply[A<:Data](implicit A: IsRational[A]): IsRational[A] = A
}

trait IsIntegral[A<:Data] extends Any with IsRational[A] {
  def ceil(a: A): A = a
  def floor(a: A): A = a
  def round(a: A): A = a
  def isWhole(a: A): Bool = Bool(true)
  def mod(a: A, b: A): A
}

object IsIntegral {
  def apply[A<:Data](implicit A: IsIntegral[A]): IsIntegral[A] = A
}

trait Real[A<:Data] extends Any with Ring[A] with ConvertableTo[A] with IsReal[A] {
  def fromRational(a: spire.math.Rational): A = fromDouble(a.toDouble)
  def fromAlgebraic(a: spire.math.Algebraic): A = fromDouble(a.toDouble)
  def fromReal(a: spire.math.Real): A = fromDouble(a.toDouble)
}

/* Integral.scala */
trait Integer[A<:Data] extends Any with Real[A] with IsIntegral[A]

object Integer {
  //implicit final val IntIsIntegral = new IntIsIntegral
  //implicit final val LongIsIntegral = new LongIsIntegral
  //implicit final val BigIntIsIntegral = new BigIntIsIntegral
  //implicit final val SafeLongIsIntegral = new SafeLongIsIntegral

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

/*@SerialVersionUID(0L)
private class IntIsIntegral extends Integral[Int] with IntIsEuclideanRing
  with ConvertableFromInt with ConvertableToInt with IntIsReal with Serializable {
  override def fromInt(n: Int): Int = n
  override def toDouble(n: Int): Double = n.toDouble
  override def toRational(n: Int): Rational = super[IntIsReal].toRational(n)
  override def toAlgebraic(n: Int): Algebraic = super[IntIsReal].toAlgebraic(n)
  override def toReal(n: Int): Real = super[IntIsReal].toReal(n)
  override def toBigInt(n: Int): BigInt = super[IntIsReal].toBigInt(n)
}

@SerialVersionUID(0L)
private[math] class LongIsIntegral extends Integral[Long] with LongIsEuclideanRing
  with ConvertableFromLong with ConvertableToLong with LongIsReal with Serializable {

  override def fromInt(n: Int): Long = n.toLong
  override def toDouble(n: Long): Double = n.toDouble
  override def toRational(n: Long): Rational = super[LongIsReal].toRational(n)
  override def toAlgebraic(n: Long): Algebraic = super[LongIsReal].toAlgebraic(n)
  override def toReal(n: Long): Real = super[LongIsReal].toReal(n)
  override def toBigInt(n: Long): BigInt = BigInt(n)
}

@SerialVersionUID(0L)
private[math] class BigIntIsIntegral extends Integral[BigInt] with BigIntIsEuclideanRing
  with ConvertableFromBigInt with ConvertableToBigInt with BigIntIsReal with Serializable {
  override def fromInt(n: Int): BigInt = BigInt(n)
  override def toDouble(n: BigInt): Double = n.toDouble
  override def toRational(n: BigInt): Rational = super[BigIntIsReal].toRational(n)
  override def toAlgebraic(n: BigInt): Algebraic = super[BigIntIsReal].toAlgebraic(n)
  override def toReal(n: BigInt): Real = super[BigIntIsReal].toReal(n)
  override def toBigInt(n: BigInt): BigInt = super[BigIntIsReal].toBigInt(n)
}

@SerialVersionUID(0L)
private[math] class SafeLongIsIntegral extends Integral[SafeLong] with SafeLongIsEuclideanRing
  with ConvertableFromSafeLong with ConvertableToSafeLong with SafeLongIsReal with Serializable {
  override def fromInt(n: Int): SafeLong = SafeLong(n)
  override def toDouble(n: SafeLong): Double = n.toDouble
  override def toRational(n: SafeLong): Rational = super[SafeLongIsReal].toRational(n)
  override def toAlgebraic(n: SafeLong): Algebraic = super[SafeLongIsReal].toAlgebraic(n)
  override def toReal(n: SafeLong): Real = super[SafeLongIsReal].toReal(n)
  override def toBigInt(n: SafeLong): BigInt = super[SafeLongIsReal].toBigInt(n)
}
*/

/* Syntax.scala */
trait EqSyntax {
  implicit def eqOps[A<:Data:Eq](a:A): EqOps[A] = new EqOps(a)
}

trait PartialOrderSyntax extends EqSyntax {
  implicit def partialOrderOps[A<:Data:PartialOrder](a:A): PartialOrderOps[A] = new PartialOrderOps(a)
}

trait OrderSyntax extends PartialOrderSyntax {
  implicit def orderOps[A<:Data:Order](a:A): OrderOps[A] = new OrderOps(a)
}

trait IsRealSyntax extends OrderSyntax with SignedSyntax {
  implicit def isRealOps[A<:Data:IsReal](a:A): IsRealOps[A] = new IsRealOps(a)
}

trait SignedSyntax {
  implicit def signedOps[A<:Data: Signed](a: A): SignedOps[A] = new SignedOps(a)
}

trait ConvertableToSyntax {
  def fromDouble[T<:Data:ConvertableTo](a: Double): T = implicitly[ConvertableTo[T]].fromDouble(a)
}

/* Ops.scala */
final class EqOps[A <: Data](lhs:A)(implicit ev:Eq[A]) {
  def ===(rhs:A): Bool = macro Ops.binop[A, Bool]
  def =!=(rhs:A): Bool = macro Ops.binop[A, Bool]
}

final class PartialOrderOps[A <: Data](lhs: A)(implicit ev: PartialOrder[A]) {
  def >(rhs: A): Bool = macro Ops.binop[A, Bool]
  def >=(rhs: A): Bool = macro Ops.binop[A, Bool]
  def <(rhs: A): Bool = macro Ops.binop[A, Bool]
  def <=(rhs: A): Bool = macro Ops.binop[A, Bool]

  def partialCompare(rhs: A): Double = macro Ops.binop[A, Double]
  def tryCompare(rhs: A): Option[Int] = macro Ops.binop[A, Option[Int]]
  def pmin(rhs: A): Option[A] = macro Ops.binop[A, A]
  def pmax(rhs: A): Option[A] = macro Ops.binop[A, A]

  def >(rhs: Int)(implicit ev1: Ring[A]): Bool = macro Ops.binopWithLift[Int, Ring[A], A]
  def >=(rhs: Int)(implicit ev1: Ring[A]): Bool = macro Ops.binopWithLift[Int, Ring[A], A]
  def <(rhs: Int)(implicit ev1: Ring[A]): Bool = macro Ops.binopWithLift[Int, Ring[A], A]
  def <=(rhs: Int)(implicit ev1: Ring[A]): Bool = macro Ops.binopWithLift[Int, Ring[A], A]

  def >(rhs: Double)(implicit ev1: Field[A]): Bool = macro Ops.binopWithLift[Int, Field[A], A]
  def >=(rhs: Double)(implicit ev1: Field[A]): Bool = macro Ops.binopWithLift[Int, Field[A], A]
  def <(rhs: Double)(implicit ev1: Field[A]): Bool = macro Ops.binopWithLift[Int, Field[A], A]
  def <=(rhs: Double)(implicit ev1: Field[A]): Bool = macro Ops.binopWithLift[Int, Field[A], A]

  def >(rhs:spire.math.Number)(implicit c:ConvertableFrom[A]): Bool = Bool(c.toNumber(lhs) > rhs)
  def >=(rhs:spire.math.Number)(implicit c:ConvertableFrom[A]): Bool = Bool(c.toNumber(lhs) >= rhs)
  def <(rhs:spire.math.Number)(implicit c:ConvertableFrom[A]): Bool = Bool(c.toNumber(lhs) < rhs)
  def <=(rhs:spire.math.Number)(implicit c:ConvertableFrom[A]): Bool = Bool(c.toNumber(lhs) <= rhs)
}

final class OrderOps[A <: Data](lhs: A)(implicit ev: Order[A]) {
  def compare(rhs: A): ComparisonBundle = macro Ops.binop[A, ComparisonBundle]
  def min(rhs: A): A = macro Ops.binop[A, A]
  def max(rhs: A): A = macro Ops.binop[A, A]

  def compare(rhs: Int)(implicit ev1: Ring[A]): Int = macro Ops.binopWithLift[Int, Ring[A], A]
  def min(rhs: Int)(implicit ev1: Ring[A]): A = macro Ops.binopWithLift[Int, Ring[A], A]
  def max(rhs: Int)(implicit ev1: Ring[A]): A = macro Ops.binopWithLift[Int, Ring[A], A]

  def compare(rhs: Double)(implicit ev1: Field[A]): Int = macro Ops.binopWithLift[Int, Field[A], A]
  def min(rhs: Double)(implicit ev1: Field[A]): A = macro Ops.binopWithLift[Int, Field[A], A]
  def max(rhs: Double)(implicit ev1: Field[A]): A = macro Ops.binopWithLift[Int, Field[A], A]

  def compare(rhs:spire.math.Number)(implicit c:ConvertableFrom[A]): Int = c.toNumber(lhs) compare rhs
  def min(rhs:spire.math.Number)(implicit c:ConvertableFrom[A]): Number = c.toNumber(lhs) min rhs
  def max(rhs:spire.math.Number)(implicit c:ConvertableFrom[A]): Number = c.toNumber(lhs) max rhs
}

final class SignedOps[A:Signed](lhs: A) {
  def abs(): A = macro Ops.unop[A]
  def sign(): Sign = macro Ops.unop[Sign]
  def signum(): Int = macro Ops.unop[Int]

  def isSignZero(): Bool = macro Ops.unop[Bool]
  def isSignPositive(): Bool = macro Ops.unop[Bool]
  def isSignNegative(): Bool = macro Ops.unop[Bool]

  def isSignNonZero(): Bool = macro Ops.unop[Bool]
  def isSignNonPositive(): Bool = macro Ops.unop[Bool]
  def isSignNonNegative(): Bool = macro Ops.unop[Bool]
}

final class IsRealOps[A<:Data](lhs:A)(implicit ev:IsReal[A]) {
  def isWhole(): Boolean = macro Ops.unop[Boolean]
  def ceil(): A = macro Ops.unop[A]
  def floor(): A = macro Ops.unop[A]
  def round(): A = macro Ops.unop[A]
  //def toDouble(): Double = macro Ops.unop[Double]
}

trait AllSyntax extends EqSyntax with PartialOrderSyntax with OrderSyntax with IsRealSyntax with SignedSyntax with
  ConvertableToSyntax

trait AllImpl extends SIntImpl with FixedPointImpl with DspRealImpl with DspComplexImpl

object implicits extends AllSyntax with AllImpl with spire.syntax.AllSyntax
