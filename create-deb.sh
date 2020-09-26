#!/bin/sh
set -eu

mkdir -p deb/DEBIAN
cp debian-control deb/DEBIAN/control

mkdir -p deb/usr/lib
cp build/bin/linuxX64/releaseShared/libdecsync.so deb/usr/lib/libdecsync.so

dpkg-deb --build deb
