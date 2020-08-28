package org.decsync.library

@ExperimentalStdlibApi
class DecsyncRealTest : DecsyncTest(::getTestDir, null)
@ExperimentalStdlibApi
class DecsyncRealTestV1 : DecsyncTest(::getTestDir, DecsyncVersion.V1)
@ExperimentalStdlibApi
class DecsyncRealTestV2 : DecsyncTest(::getTestDir, DecsyncVersion.V2)