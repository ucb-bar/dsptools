* *RealBits* extends *Real* with *BinaryRepresentation* and *ChiselConvertableFrom*
* *IntegerBits* extends *Integer* with *BinaryRepresentation* and *ChiselConvertableFrom*

These form the underlying types of all Chisel numeric type classes. 

# BinaryRepresentation

This adds additional functions to Chisel base numeric types (i.e. UInt, SInt, FixedPoint, DspReal)

* shl (Int or UInt amount)
* shr (Int or UInt amount)
* signBit

