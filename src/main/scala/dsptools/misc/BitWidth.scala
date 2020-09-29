// SPDX-License-Identifier: Apache-2.0

package dsptools.misc

object BitWidth {
  /**
    * Utility function that computes bits required for a number
    *
    * @param n number of interest
    * @return
    */
  def computeBits(n: BigInt): Int = {
    n.bitLength + (if(n < 0) 1 else 0)
  }

  /**
    * return the smallest number of bits required to hold the given number in
    * an SInt
    * Note: positive numbers will get one minimum width one higher than would be
    * required for a UInt
    *
    * @param num number to find width for
    * @return minimum required bits for an SInt
    */
  def requiredBitsForSInt(num: BigInt): Int = {
    if(num == BigInt(0) || num == -BigInt(1)) {
      1
    }
    else {
      if (num < 0) {
        computeBits(num)
      }
      else {
        computeBits(num) + 1
      }
    }
  }

  def requiredBitsForSInt(low: BigInt, high: BigInt): Int = {
    requiredBitsForSInt(low).max(requiredBitsForSInt(high))
  }

  /**
    * return the smallest number of bits required to hold the given number in
    * an UInt
    * Note: positive numbers will get one minimum width one higher than would be
    * required for a UInt
    *
    * @param num number to find width for
    * @return minimum required bits for an SInt
    */
  def requiredBitsForUInt(num: BigInt): Int = {
    if(num == BigInt(0)) {
      1
    }
    else {
      computeBits(num)
    }
  }
}