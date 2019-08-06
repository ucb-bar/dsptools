Abstract Algebra Type Classes ([Spire](https://github.com/non/spire))

Listed here are hierarchically presented type classes and operations associated with them that return Chisel base types (Bool or numbers). These operations can be used in a Chisel DSP design. 

> Note that when classes are extended, subclass def's have the most precedence

# Ring (Spire base)
* plus (+)
* minus (-)
* negate (unary -)
* times (*)
* one
* zero

> Note: +, -, negate, * all have associated pipelining amounts [+, -, negate associated with numAddPipes; * associated with numMulPipes set in DspContext]. Additionally, you can control the overflow behavior of +, -, negate via overflowType in DspContext. Finally, for *, you can control binary trim behavior for FixedPoint #'s via trimType and binaryPointGrowth in DspContext.

# Eq
* eqv (===)
* neqv (=!=)

# PartialOrder extends Eq 
* *Not practically useful for Chisel base numbers; assumes inputs can be invalid!*
* eqv (===)
* lteqv (<=)
* lt (<)
* gteqv (>=)
* gt (>)

# Order extends PartialOrder
* eqv (===)
* gt (>)
* lt (<)
* gteqv (>=)
* lteqv (<=)
* min
* max

# Signed
* abs
* isSignZero
* isSignPositive
* isSignNegative
* isSignNonZero
* isSignNonPositive
* isSignNonNegative