libdecsync
==========

libdecsync is a multiplatform library for synchronizing using [DecSync](https://github.com/39aldo39/DecSync).

Build from source (native)
--------------------------

Install dependencies:

### Debian/Ubuntu
```
sudo apt install \
	build-essential \
	openjdk-8-jdk \
	libncurses5
```

### Fedora
```
sudo dnf install \
	make \
	java-1.8.0-openjdk-headless \
	ncurses-compat-libs
```

### Build
```
git clone https://github.com/39aldo39/libdecsync
cd libdecsync
make
sudo make install
```

This installs the shared library `libdecsync.so`, the header `libdecsync.h` (and `libdecsync_api.h`) and the pkg-config file `decsync.pc`. For documentation on how to use the library, see [libdecsync.h](src/linuxX64Main/libdecsync.h). The shared library contains the same functions, but without type information. Moreover, these functions have the prefix `decsync_so` instead of `decsync`.

Build from source (android)
---------------------------

```
./gradlew assembleRelease
```

This creates the aar-file `build/outputs/aar/libdecsync-release.aar`.

Donations
---------

### PayPal
[![](https://www.paypalobjects.com/en_US/i/btn/btn_donateCC_LG.gif)](https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=4V96AFD3S4TPJ)

### Bitcoin
[`1JWYoV2MZyu8LYYHCur9jUJgGqE98m566z`](bitcoin:1JWYoV2MZyu8LYYHCur9jUJgGqE98m566z)
