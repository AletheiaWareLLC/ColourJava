ColourJava
==========

This is a Java implementation of Colour - an exploration of colour through collaborative, blockchain-backed, digital canvases.

Setup
=====
Libraries

    mkdir libs
    ln -s <bcjavalib> libs/BCJava.jar
    ln -s <aliasjavalib> libs/AliasJava.jar
    ln -s <financejavalib> libs/FinanceJava.jar
    ln -s <protolib> libs/protobuf-lite-3.0.1.jar

Protocol Buffers

    cd <path/to/Colour>
    ./build.sh --javalite_out=<path/to/ColourJava>/source/

Build
=====

    ./build.sh
