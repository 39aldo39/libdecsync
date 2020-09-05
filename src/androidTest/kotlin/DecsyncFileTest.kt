package org.decsync.library

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@ExperimentalStdlibApi
class DecsyncFileSysTest : DecsyncFileTest(
        getTestDirSys(InstrumentationRegistry.getInstrumentation().context)
)