* *RealBits* extends *Real* with *BinaryRepresentation* and *ChiselConvertableFrom*
* *IntegerBits* extends *RealBits* with *Integer*

These form the underlying types of all Chisel numeric type classes. 

# BinaryRepresentation

This adds additional functions to Chisel base numeric types (i.e. UInt, SInt, FixedPoint, DspReal)

* shl (Int or UInt amount)
* shr (Int or UInt amount) -- Rounds to negative infinity i.e. for negative #'s, continual >> will never be smaller in magnitude than -LSB
* signBit

* div2 (More proper division than shr i.e. rounds [choice via DSP Context] for SInt and adds more binary points for FixedPoint [Can be trimmed via context])
* mul2 (identical to shl by an Int amount)
* trimBinary (trim fractional bits with rounding mode selected via DspContext; doesn't affect DspReal)

