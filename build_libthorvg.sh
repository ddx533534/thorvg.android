#!/bin/bash

cd thorvg
rm -rf build
meson setup build -Dloaders="svg" --cross-file /tmp/android_cross.txt -Ddefault_library=static
ninja -C build