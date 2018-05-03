#!/bin/bash

cd $INSTALL_DIR/
git clone "https://github.com/freechipsproject/rocket-chip.git"
cd rocket-chip
git submodule update --init firrtl chisel3 hardfloat
sbt publishLocal
