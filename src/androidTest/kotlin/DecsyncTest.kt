package org.decsync.library

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@ExperimentalStdlibApi
class DecsyncSysTest : DecsyncTest(
        { getTestDirSys(InstrumentationRegistry.getInstrumentation().context) },
        null
)
@RunWith(AndroidJUnit4::class)
@ExperimentalStdlibApi
class DecsyncSysTestV1 : DecsyncTest(
        { getTestDirSys(InstrumentationRegistry.getInstrumentation().context) },
        DecsyncVersion.V1
)
@RunWith(AndroidJUnit4::class)
@ExperimentalStdlibApi
class DecsyncSysTestV2 : DecsyncTest(
        { getTestDirSys(InstrumentationRegistry.getInstrumentation().context) },
        DecsyncVersion.V2
)

@RunWith(AndroidJUnit4::class)
@ExperimentalStdlibApi
class DecsyncUpgradeSysTestV1V2 : DecsyncUpgradeTest(
        { getTestDirSys(InstrumentationRegistry.getInstrumentation().context) },
        DecsyncVersion.V1,
        DecsyncVersion.V2
)