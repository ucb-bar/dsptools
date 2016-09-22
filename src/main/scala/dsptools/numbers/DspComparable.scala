package dsptools.numbers

import chisel3.util.{Valid, ValidIO}
import chisel3.{Bool, Bundle, Data, Mux}
import spire.algebra.{Field, Ring}
import spire.macros.Ops
import spire.math.{ConvertableFrom, Number}
import scala.language.experimental.macros

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
trait Eq[@specialized A <: Data] extends Any {
  /** Returns `true` if `x` and `y` are equivalent, `false` otherwise. */
  def eqv(x:A, y:A): Bool

  /** Returns `false` if `x` and `y` are equivalent, `true` otherwise. */
  def neqv(x:A, y:A): Bool = !eqv(x, y)

  /**
    * Constructs a new `Eq` instance for type `B` where 2 elements are
    * equivalent iff `eqv(f(x), f(y))`.
    */
  def on[@specialized B <: Data](f:B => A): Eq[B] = new MappedEq(this)(f)
}

private[numbers] class MappedEq[@specialized A <: Data, @specialized B <: Data](eq: Eq[B])(f: A => B) extends Eq[A] {
  def eqv(x: A, y: A): Bool = eq.eqv(f(x), f(y))
}

object Eq {
  def apply[A <: Data](implicit e:Eq[A]):Eq[A] = e

  def by[@specialized A <: Data, @specialized B <: Data](f:A => B)(implicit e:Eq[B]): Eq[A] = new MappedEq(e)(f)
}


class ComparisonBundle extends Bundle {
  val eq = Bool()
  val lt = Bool()
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
trait PartialOrder[@specialized A <: Data] extends Any with Eq[A] {
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
  override def on[@specialized B <: Data](f: B => A): PartialOrder[B] = new MappedPartialOrder(this)(f)

  /**
    * Defines a partial order on `A` where all arrows switch direction.
    */
  def reverse: PartialOrder[A] = new ReversedPartialOrder(this)
}

private[numbers] class MappedPartialOrder[@specialized A <: Data, @specialized B <: Data](partialOrder: PartialOrder[B])(f: A => B) extends PartialOrder[A] {
  def partialCompare(x: A, y: A): ValidIO[ComparisonBundle] = partialOrder.partialCompare(f(x), f(y))
}

private[numbers] class ReversedPartialOrder[@specialized A <: Data](partialOrder: PartialOrder[A]) extends PartialOrder[A] {
  def partialCompare(x: A, y: A): ValidIO[ComparisonBundle] = partialOrder.partialCompare(y, x)
}

object PartialOrder {
  @inline final def apply[A <: Data](implicit po: PartialOrder[A]): PartialOrder[A] = po

  def by[@specialized A <: Data, @specialized B <: Data](f: A => B)(implicit po: PartialOrder[B]): PartialOrder[A] = po.on(f)

  def from[@specialized A <: Data](f: (A, A) => ValidIO[ComparisonBundle]): PartialOrder[A] = new PartialOrder[A] {
    def partialCompare(x: A, y: A): ValidIO[ComparisonBundle] = f(x, y)
  }

/*  implicit def partialOrdering[A <: Data](implicit po: PartialOrder[A]): PartialOrdering[A] = new PartialOrdering[A] {
    def partialCompare(x: A, y: A): ValidIO[ComparisonBundle] = po.partialCompare(x, y)
    def lteq(x: A, y: A): Bool = po.lteqv(x, y)
  }
  */
}

/*
private[numbers] class DerivedPartialOrdering[@specialized A <: Data](partialOrder: PartialOrder[A]) extends PartialOrdering[A] {
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
trait Order[@specialized A <: Data] extends Any with PartialOrder[A] {
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
  override def on[@specialized B <: Data](f: B => A): Order[B] = new MappedOrder(this)(f)

  /**
    * Defines an ordering on `A` where all arrows switch direction.
    */
  override def reverse: Order[A] = new ReversedOrder(this)
}

private[numbers] class MappedOrder[@specialized A <: Data, @specialized B <: Data](order: Order[B])(f: A => B) extends Order[A] {
  def compare(x: A, y: A): ComparisonBundle = order.compare(f(x), f(y))
}

private[numbers] class ReversedOrder[@specialized A <: Data](order: Order[A]) extends Order[A] {
  def compare(x: A, y: A): ComparisonBundle = order.compare(y, x)
}

object Order {
  @inline final def apply[A <: Data](implicit o: Order[A]): Order[A] = o

  def by[@specialized A <: Data, @specialized B <: Data](f: A => B)(implicit o: Order[B]): Order[A] = o.on(f)

  def from[@specialized A <: Data](f: (A, A) => ComparisonBundle): Order[A] = new Order[A] {
    def compare(x: A, y: A): ComparisonBundle = f(x, y)
  }

  /*implicit def ordering[A <: Data](implicit o: Order[A]): Ordering[A] = new Ordering[A] {
    def compare(x: A, y: A): ComparisonBundle = o.compare(x, y)
  }
  */
}

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

/* TODO
trait IsRealSyntax extends OrderSyntax with SignedSyntax {
  implicit def isRealOps[A:IsReal](a:A): IsRealOps[A] = new IsRealOps(a)
}

trait SignedSyntax {
  implicit def signedOps[A: Signed](a: A): SignedOps[A] = new SignedOps(a)
}
*/

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

  def >(rhs:Number)(implicit c:ConvertableFrom[A]): Bool = Bool(c.toNumber(lhs) > rhs)
  def >=(rhs:Number)(implicit c:ConvertableFrom[A]): Bool = Bool(c.toNumber(lhs) >= rhs)
  def <(rhs:Number)(implicit c:ConvertableFrom[A]): Bool = Bool(c.toNumber(lhs) < rhs)
  def <=(rhs:Number)(implicit c:ConvertableFrom[A]): Bool = Bool(c.toNumber(lhs) <= rhs)
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

  def compare(rhs:Number)(implicit c:ConvertableFrom[A]): Int = c.toNumber(lhs) compare rhs
  def min(rhs:Number)(implicit c:ConvertableFrom[A]): Number = c.toNumber(lhs) min rhs
  def max(rhs:Number)(implicit c:ConvertableFrom[A]): Number = c.toNumber(lhs) max rhs
}

/*final class SignedOps[A:Signed](lhs: A) {
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
*/

trait AllSyntax extends EqSyntax with PartialOrderSyntax with OrderSyntax

object implicits extends AllSyntax
