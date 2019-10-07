prefix?=/usr
BUILD_DIR=build/bin/linuxX64/releaseShared

INSTALL=install
RM=rm -f

SOURCES=$(wildcard src/*/kotlin/org/decsync/library/*.kt)
PC_PREFIX:=prefix=$(prefix)

.PHONY: all
all: $(BUILD_DIR)/libdecsync_api.h $(BUILD_DIR)/libdecsync.so $(BUILD_DIR)/decsync.pc

$(BUILD_DIR)/libdecsync_api.h $(BUILD_DIR)/libdecsync.so: $(SOURCES)
	./gradlew linkReleaseSharedLinuxX64

$(BUILD_DIR)/decsync.pc: src/linuxX64Main/decsync.pc.in
	$(file > $(BUILD_DIR)/decsync.pc,$(PC_PREFIX))
	cat src/linuxX64Main/decsync.pc.in >> $(BUILD_DIR)/decsync.pc

.PHONY: install
install: $(BUILD_DIR)/libdecsync_api.h $(BUILD_DIR)/libdecsync.so $(BUILD_DIR)/decsync.pc
	$(INSTALL) -d $(DESTDIR)$(prefix)/include
	$(INSTALL) -m 644 $(BUILD_DIR)/libdecsync_api.h $(DESTDIR)$(prefix)/include
	$(INSTALL) -m 644 src/linuxX64Main/libdecsync.h $(DESTDIR)$(prefix)/include
	$(INSTALL) -d $(DESTDIR)$(prefix)/lib
	$(INSTALL) -m 644 $(BUILD_DIR)/libdecsync.so $(DESTDIR)$(prefix)/lib
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
