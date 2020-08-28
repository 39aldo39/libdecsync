package org.decsync.library

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.runner.RunWith
import java.io.File

fun getTestDirSys(context: Context): NativeFile {
    return nativeFileFromFile(getTestFile(context))
}

private fun getTestFile(context: Context): File = File(context.cacheDir, "DecSync")

@RunWith(AndroidJUnit4::class)
@ExperimentalStdlibApi
class NativeFileSysTest : NativeFileTest(
        getTestDirSys(InstrumentationRegistry.getInstrumentation().context)
)