Here, we list number types that can be represented with Chisel base numbers.

* A number that *IsReal* has *Order* and is *Signed*. 
* A number that *IsAlgebraic* also *IsReal*. 
* A number that *IsRational* also *IsAlgebraic*.
* A number that *IsIntegral* also *IsRational*. 

* A *Real* number *IsReal* and can be operated on in a *Ring*. Some type conversion is supported.
* An *Integer* is a *Real* number that also *IsIntegral*. 

# IsReal
* ceil
* floor
* round (0.5 fractional part -> rounded to integer with the larger absolute value)
* isWhole

# IsIntegral
* ceil
* floor
* round
* isWhole
* mod
* isOdd
* isEven