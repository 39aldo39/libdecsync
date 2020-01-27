package org.decsync.library

import kotlinx.cinterop.*
import platform.posix.*

actual fun getDeviceName(): String {
    val name = ByteArray(256)
    name.usePinned { namePin ->
        if (gethostname(namePin.addressOf(0), name.size - 1) != 0) {
            throw Exception("Failed to get host name")
        }
    }
    return name.toKString()
}

actual fun currentDatetime(): String {
    val now = time(null)
    val tm = gmtime(cValuesOf(now))?.pointed ?: throw Exception("Failed to get current time")
    val year = (tm.tm_year + 1900).toString()
    val mon = (tm.tm_mon + 1).toString().padStart(2, '0')
    val day = tm.tm_mday.toString().padStart(2, '0')
    val hour = tm.tm_hour.toString().padStart(2, '0')
    val min = tm.tm_min.toString().padStart(2, '0')
    val sec = tm.tm_sec.toString().padStart(2, '0')
    return "$year-$mon-${day}T$hour:$min:$sec"
}

fun getDefaultDecsyncDir(): String =
        getenv("DECSYNC_DIR")?.toKString() ?: getenv("USERPROFILE")!!.toKString() + "/DecSync"

actual fun byteArrayToString(input: ByteArray): String = input.toKString()