* *RealBits* extends *Real* with *ChiselBaseNum* and *ChiselConvertableFrom*
* *IntegerBits* extends *Integer* with *ChiselBaseNum* and *ChiselConvertableFrom*

These form the underlying types of all Chisel numeric type classes. 

# ChiselBaseNum

This adds additional functions to Chisel base numeric types (i.e. UInt, SInt, FixedPoint, DspReal)

* shl (Int or UInt amount)
* shr (Int or UInt amount)
* signBit

