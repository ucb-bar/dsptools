ROCKET-DSP-UTILS
===================

[![Test](https://github.com/chick/rocket-dsp-utils/actions/workflows/test.yml/badge.svg)](https://github.com/ucb-bar/dsptools/actions/workflows/test.yml)

This repository is part of a transition to move the rocket subdirectory from 
[ucb-bar/dsptools]() to its own repository

----------

This README will be filled out later. At the moment it will only contain instructions to run it locally

Goals: Get the rocket sub-project of dsptools to run within the chipyard environment.
It is based on running using the chipyards rocket-chip commit

Steps
- Checkout [rocket-chip](https://github.com/chipsalliance/rocket-chip)
  - git checkout 3b3169cb04bd5a7be4ec0be04a4cbe1a794c540e
  - sbt
    - rocket-macros / publishLocal
    - api-config-chipsalliance / publishLocal
    - publishLocal
  - should be able to edit/compile/run things
    - I have not run all tests yet.
  - PLEASE let me know if this does not work for you
  
Questions:
- Questionable code is marked with //TODO: CHIPYARD
- Using local publishing of everything was just to get jump started, can modify build approach after getting things working

    
This code is maintained by [Chick](https://github.com/chick)

Copyright (c) 2015 - 2021 The Regents of the University of California. Released under the Modified (3-clause) BSD license.
