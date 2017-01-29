package dsptools.numbers

/* Needs to be redefined from spire */
object Ring {
  def apply[A <: Data](implicit A: Ring[A]): Ring[A] = A
}