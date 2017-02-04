Black boxes useful functions that operate on "real" number types.

# Operations that have FixedPoint (and other Chisel number type) equivalents
* Add
* Subtract
* Multiply
* Divide (Chisel only deals with division by powers of 2)
* >
* >=
* <
* <=
* ==
* !=
* Floor
* Ceil

# Conversion functions between synthesizable Chisel number types + DspReal (use with caution!)

> Note: These are only good for golden model testing. They should *never* be used in your final design. 

* SInt -> Real
* Real -> SInt

# Non-synthesizable operations that don't have Chisel number type equivalents

> Note: These work with Verilator + FIRRTL interpreter, but can't be used with type classes.

* Ln
* Log10
* Exp
* Sqrt
* Pow

# Non-synthesizable operations 

> These don't have Chisel number type equivalents and work with FIRRTL interpreter. Verilator doesn't support these :( but we built out approximation functions with no guarantees on precision...)

* Sin
* Cos
* Tan
* ASin
* ACos
* ATan
* ATan2
* Hypot
* Sinh
* Cosh
* Tanh
* ASinh
* ACosh
* ATanh