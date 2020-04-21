Abstract Algebra Type Classes ([Spire](https://github.com/non/spire))

Listed here are hierarchically presented type classes and operations associated with them that return Chisel base types (Bool or numbers).

# `Ring` (based on `Ring` from Spire)
* plus (+)
* minus (-)
* negate (unary_-)
* times (*)
* one
* zero
* context_+
* context_- (binary and unary)
* context_*

> Note: `context_+`, `context_-`, and `context_*` all have associated pipelining amounts [+, -, negate associated with `numAddPipes`; * associated with `numMulPipes` set in `DspContext`]. You can control the overflow behavior of `context_+` and `context_-` via `overflowType` in `DspContext`. Finally, for *, you can control binary trim behavior for `FixedPoint` #'s via `trimType` and `binaryPointGrowth` in `DspContext`.

# `Eq`
* eqv (===)
* neqv (=!=)

# `PartialOrder extends Eq `
* *Not likely to be practically useful for many Chisel designs, but it is part of the hierarchy in Spire*
* eqv (===)
* lteqv (<=)
* lt (<)
* gteqv (>=)
* gt (>)

# `Order extends PartialOrder`
* eqv (===)
* gt (>)
* lt (<)
* gteqv (>=)
* lteqv (<=)
* min
* max

# `Signed`
* abs
* isSignZero
* isSignPositive
* isSignNegative
* isSignNonZero
* isSignNonPositive
* isSignNonNegative
