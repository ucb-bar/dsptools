#!/bin/bash

cd $INSTALL_DIR/
rm -rf rocket-chip/
git clone -b sbtUpdates  --single-branch "https://github.com/grebe/rocket-chip.git"
cd rocket-chip
git submodule update --init firrtl chisel3 hardfloat
sbt publishLocal
