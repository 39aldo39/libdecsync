package org.decsync.library

fun getTestDir(): NativeFile {
    return nativeFileFromPath("/tmp/tests/decsync")
}

@ExperimentalStdlibApi
class NativeFileRealTest : NativeFileTest(getTestDir())