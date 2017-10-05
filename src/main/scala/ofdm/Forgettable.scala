package ofdm

import chisel3._
import chisel3.util.Valid
import dsptools.numbers._

object Forgettable {
  def apply[T <: Data : Ring](in: Valid[T], ff: T, tpe: Option[T] = None, init: Option[T] = None): T = {
    val forgettable = Reg(tpe.getOrElse(ff.cloneType))
    init.map { forgettable := _ }
    when (in.fire()) {
      forgettable := in.bits + ff * forgettable
    }
    forgettable
  }
}
