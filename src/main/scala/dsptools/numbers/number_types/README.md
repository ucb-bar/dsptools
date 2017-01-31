Here, we list number types that can be represented with Chisel base numbers.

* A number that *IsReal* has *Order* and is *Signed*. 
* A number that *IsIntegral* also *IsReal*. 

* A *Real* number *IsReal* and can be operated on in a *Ring*. Some type conversion is supported via *ConvertableTo* (i.e. *fromDouble*).
* An *Integer* is a *Real* number that also *IsIntegral*. 

# IsReal
* ceil
* floor
* truncate (integer portion)
* round (0.5 fractional part -> tie breaking to positive infinity i.e. round half up)
* isWhole

> See [Rounding Modes Wiki](https://en.wikipedia.org/wiki/Rounding) for properties. 

# IsIntegral
* ceil
* floor
* truncate
* round
* isWhole
* mod
* isOdd
* isEven