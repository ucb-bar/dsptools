# Blackbox Compatibility
A number of operations for `DspReal`s have been implemented via Chisel blackboxes.
Some operations have equivalents that are synthesizeable- these are useful for testing `FixedPoint` designs with better finite precision effects.
Some operations do not have synthesizeable equivalents- these are useful for modeling (e.g. an ADC).

## Operations that are equivalents for synthesizeable types (e.g. FixedPoint)
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

## Conversion functions between synthesizable Chisel number types + DspReal (non-synthesizeable, use with caution!)

> Note: These are only good for golden model testing. They should *never* be used in your final design. 

* SInt -> Real
* Real -> SInt

## Non-synthesizable operations that don't have Chisel number type equivalents (with Verilator support)

> Note: These work with Verilator or Treadle, but can't be used with type classes.

* Ln
* Log10
* Exp
* Sqrt
* Pow

## Non-synthesizable operations (no Verilator support)

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
