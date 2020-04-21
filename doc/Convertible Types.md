# `ConvertableTo`
* fromDouble (to type; gets binary point from type -- if needed)
* fromDoubleWithFixedWidth (to type; gets binary point *and* width from type -- if needed)

# `ChiselConvertableFrom` (Specific to Chisel base number types! I.e. UInt, SInt, FixedPoint, DspReal)
* intPart (takes the integer portion without rounding and converts it to an SInt)
* asFixed (provide the prototype to get a binary point location)
* asReal

> **WARNING**: Binary points and widths *should* be known if these methods are going to be used!
