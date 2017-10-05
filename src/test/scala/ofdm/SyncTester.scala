package ofdm

import chisel3._
import chisel3.iotesters.PeekPokeTester
import dsptools.DspTester

class SyncTester[T <: Data](c: Sync[T]) extends DspTester(c) {

}
