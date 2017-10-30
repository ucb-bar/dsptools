package generatortools.io

import chisel3._
import scala.collection.immutable.ListMap

class CustomBundle[T <: Data](elts: (String, T)*) extends Record {
  /** ListMap of key (as string), Data pairs */
  val elements: ListMap[String, T] = ListMap(elts map { case (field, elt) => field -> elt.chiselCloneType }: _*)
  /** Gets elements as sequence */
  def seq: Seq[T] = elements.toSeq.map(_._2)
  /** Tries to get the element called name (String) */
  def apply(name: String): T = {
    require(elements isDefinedAt name, "String key invalid!")
    elements(name)
  }
  /** Tries to get the element indexed by idx (Int) */
  def apply(idx: Int): T = {
    val strIdx = idx.toString
    require(elements isDefinedAt strIdx, s"Index $strIdx invalid!")
    elements(strIdx)
  }
  /** Tries to convert elements to ListMap[Int, T] -- NOT A CLONE */
  def indexedElements: ListMap[Int, T] = elements map { case (field, elt) =>
    val validIdx = try {
      Some(field.toInt)
    } catch {
      case e: NumberFormatException => None
    }
    validIdx match {
      case Some(idx) => idx -> elt
      case None => throw new Exception("CustomBundle isn't indexed!")
    }
  }
  /** Necessary Chisel helper for cloning this data type -- DON'T CHANGE */
  override def cloneType = new CustomBundle(elements.toList: _*).asInstanceOf[this.type]
}

object CustomBundle {
  /** Creates a custom string-addressed bundle with elements of type gen */
  def withKeys[T <: Data](gen: T, keys: Seq[String]) = new CustomBundle(keys.map(_ -> gen): _*)
  /** Creates a custom indexed bundle (indexes up to you) with elements of type gen */
  def apply[T <: Data](gen: T, idxs: Seq[Int]) = new CustomBundle(idxs.map(_.toString -> gen): _*)
  /** Creates normally indexed bundle of elements with various types.
    * Allows Vecs of elements of different types/widths.
    */
  def apply[T <: Data](gen: Seq[T]) =
    new CustomBundle(gen.zipWithIndex.map{ case (elt, field) => field.toString -> elt }: _*)
  /** Creates a wire and assigns to it */
  def wire[T <: Data](gen: Seq[T]) = {
    val result = Wire(CustomBundle(gen))
    result.seq.zip(gen) foreach { case (lhs, rhs) => lhs := rhs }
    result
  }
}