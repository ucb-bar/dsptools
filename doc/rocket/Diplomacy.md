# Diplomacy
Diplomatic nodes are defined in [rocketchip](https://github.com/chipsalliance/rocket-chip/blob/master/src/main/scala/diplomacy/Nodes.scala).
An overview of how diplomacy works can be found from CARRV 2017 [here](https://carrv.github.io/2017/papers/cook-diplomacy-carrv2017.pdf) and [here](https://carrv.github.io/2017/slides/cook-diplomacy-carrv2017-slides.pdf).
The basic idea is that interfaces (e.g. an AXI-4 memory interface) need to be parameterized to satisfy both the master and slave side of an interface.
Diplomacy is a system by which master and slaves can specify parameters and have a mechanism to resolve if a connection can be formed, and if so what parameters will satisfy both sides.
This is system is called diplomacy.
It is very general and can be applied to any parameterized interface, but in practice it is often used to parameterize memory interfaces.
Rocketchip contains diplomatic implementations of AHB, APB, AXI-4, and TileLink.
Dsptools contains an implementation of AXI4-Stream that is diplomatic, documented [here](AXI4-Stream.md).

