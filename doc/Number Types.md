In writing a generator, you will probably use typeclasses like `Real` or `Integer` that map naturally to many of the Chisel types you are familiar with.
These typeclasses (implemented in Scala as `trait`s) extend a number of more granular typeclasses.

Library authors may find it useful to understand these more granular typeclasses so they can write generators that work for both `Real` and `Integer` typeclasses, for example.

* A number that *IsIntegral* also *IsReal*. 

* A *Real* number *IsReal* and can be operated on in a *Ring*. Some type conversion is supported via *ConvertableTo* (e.g. *fromDouble*).
* An *Integer* is a *Real* number that also *IsIntegral*. 

# `IsReal`
* inherits all operations from `Order` and `Signed`
* `ceil`
* `floor`
* `truncate` (integer portion)
* `round` (0.5 fractional part -> tie breaking to positive infinity i.e. round half up)
* `isWhole`

> See [Rounding Modes Wiki](https://en.wikipedia.org/wiki/Rounding) for properties. 

# `IsIntegral`
* inherits all operations from `IsReal`
* `mod`
* `isOdd`
* `isEven`
