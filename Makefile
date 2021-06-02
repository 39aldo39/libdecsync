prefix?=/usr
libdir?=$(prefix)/lib

INSTALL=install
RM=rm -f

uname_m := $(shell uname -m)
ifeq ($(uname_m),x86_64)
  platform=linuxX64
else ifeq ($(uname_m:arm%=),)
  bits := $(shell getconf LONG_BIT)
  ifeq ($(bits),64)
    platform=linuxArm64
  else
    platform=linuxArm32Hfp
  endif
else
  $(error Unsupported platform $(uname_m))
endif

BUILD_DIR=build/bin/$(platform)/releaseShared
SOURCES=$(wildcard src/*/kotlin/org/decsync/library/*.kt)
PC_PREFIX:=prefix=$(prefix)

.PHONY: all
all: $(BUILD_DIR)/libdecsync_api.h $(BUILD_DIR)/libdecsync.so $(BUILD_DIR)/decsync.pc

$(BUILD_DIR)/libdecsync_api.h $(BUILD_DIR)/libdecsync.so: $(SOURCES)
	./gradlew linkReleaseShared$(platform)

$(BUILD_DIR)/decsync.pc: src/linuxMain/decsync.pc.in
	$(file > $(BUILD_DIR)/decsync.pc,$(PC_PREFIX))
	cat src/linuxMain/decsync.pc.in >> $(BUILD_DIR)/decsync.pc

.PHONY: install
install: $(BUILD_DIR)/libdecsync_api.h $(BUILD_DIR)/libdecsync.so $(BUILD_DIR)/decsync.pc
	$(INSTALL) -d $(DESTDIR)$(prefix)/include
	$(INSTALL) -m 644 $(BUILD_DIR)/libdecsync_api.h $(DESTDIR)$(prefix)/include
	$(INSTALL) -m 644 src/linuxMain/libdecsync.h $(DESTDIR)$(prefix)/include
	$(INSTALL) -d $(DESTDIR)$(libdir)
	$(INSTALL) -m 644 $(BUILD_DIR)/libdecsync.so $(DESTDIR)$(libdir)
	$(INSTALL) -d $(DESTDIR)$(prefix)/share/pkgconfig
	$(INSTALL) -m 644 $(BUILD_DIR)/decsync.pc $(DESTDIR)$(prefix)/share/pkgconfig

.PHONY: uninstall
uninstall:
	$(RM) $(DESTDIR)$(prefix)/include/libdecsync_api.h
	$(RM) $(DESTDIR)$(prefix)/include/libdecsync.h
	$(RM) $(DESTDIR)$(prefix)/lib/libdecsync.so
	$(RM) $(DESTDIR)$(prefix)/share/pkgconfig/decsync.pc

.PHONY: clean
clean:
	./gradlew clean
