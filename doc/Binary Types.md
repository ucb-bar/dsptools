* *RealBits* extends *Real* with *BinaryRepresentation* and *ChiselConvertableFrom*
* *IntegerBits* extends *RealBits* with *Integer*

These form the underlying types of all Chisel numeric type classes. 

# BinaryRepresentation
Typeclasses like `Real` or `Integer` are useful because they abstract away the details of how the underlying numbers are represented, allowing abstract generators to be reused.
Sometimes, knowledge of the underlying representation of a number can be used to implement some operations more efficiently.
A well known example for binary representations of numbers is that multiplying or dividing by a power of two can be implemented efficiently with shifts.

The `BinaryRepresentation` typeclass adds functions for implementing multiplication and division (and some other small utilities) by powers of two using shifts:
* `shl` (Int or UInt amount)
* `shr` (Int or UInt amount) -- Rounds to negative infinity i.e. for negative #'s, continual >> will never be smaller in magnitude than -LSB
* `signBit`
* `div2` (More proper division than shr i.e. rounds [choice via DSP Context] for SInt and adds more binary points for FixedPoint [Can be trimmed via context])
* `mul2` (identical to shl by an Int amount)
* `trimBinary` (trim fractional bits with rounding mode selected via DspContext; doesn't affect DspReal)

