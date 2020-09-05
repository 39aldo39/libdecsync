package org.decsync.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@ExperimentalStdlibApi
class UrlTest {
    @Test
    fun encode() {
        assertEquals("basic", Url.encode("basic"))
        assertEquals("safe-_.~", Url.encode("safe-_.~"))
        assertEquals("%2Ea.", Url.encode(".a."))
        assertEquals("%60%21%40%23%24%25%5E%26%2A%28%29%3D%2B%2F", Url.encode("`!@#$%^&*()=+/"))
        assertEquals("%E2%98%BA", Url.encode("\u263A"))
        assertEquals("%F0%9F%8C%88", Url.encode("\uD83C\uDF08"))
    }

    @Test
    fun decodeSuccess() {
        assertEquals("basic", Url.decode("basic"))
        assertEquals("safe-_.~", Url.decode("safe-_.~"))
        assertEquals(".a.", Url.decode("%2Ea."))
        assertEquals("`!@#\$%^&*()=+", Url.decode("%60%21%40%23%24%25%5E%26%2A%28%29%3D%2B"))
        assertEquals("\u263A", Url.decode("%E2%98%BA"))
        assertEquals("\uD83C\uDF08", Url.decode("%F0%9F%8C%88"))
    }

    @Test
    fun decodeFail() {
        assertNull(Url.decode("%"))
        assertNull(Url.decode("%1"))
        assertNull(Url.decode("%GG"))
        assertNull(Url.decode("%4a"))
        assertNull(Url.decode(".foo"))
        assertNull(Url.decode("!@#$"))
    }
}