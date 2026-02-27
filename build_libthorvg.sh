#!/bin/bash

cd thorvg
rm -rf build
meson setup build -Dloaders="svg,lottie" --strip --optimization=2 --buildtype=release --cross-file /tmp/android_cross.txt -Ddefault_library=static
ninja -C build