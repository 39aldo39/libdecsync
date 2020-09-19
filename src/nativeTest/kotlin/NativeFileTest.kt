package org.decsync.library

fun getTestDir(): NativeFile {
    return nativeFileFromPath(".tests/decsync")
}

@ExperimentalStdlibApi
class NativeFileRealTest : NativeFileTest(getTestDir())