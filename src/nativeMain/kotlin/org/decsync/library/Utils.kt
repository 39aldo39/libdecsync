/**
 * libdecsync - Utils.kt
 *
 * Copyright (C) 2019 Aldo Gunsing
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation.
 *
 * This library is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, see <http://www.gnu.org/licenses/>.
 */

package org.decsync.library

import kotlinx.cinterop.*
import platform.posix.*

expect fun Int.off_t(): off_t
expect val openFlagsBinary: Int
expect fun mkdirCustom(path: String, mode: Int)
expect fun readCustom(fd: Int, buf: CValuesRef<*>?, len: Int)
expect fun writeCustom(fd: Int, buf: CValuesRef<*>?, size: Int)
expect fun gethostnameCustom(name: CValuesRef<ByteVar>, size: Int): Int

actual fun getDeviceName(): String {
    val name = ByteArray(256)
    name.usePinned { namePin ->
        if (gethostnameCustom(namePin.addressOf(0), name.size - 1) != 0) {
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

actual fun byteArrayToString(input: ByteArray): String = input.toKString()

// Native is fast enough
actual fun async(f: () -> Unit) = f()

expect fun getDefaultDecsyncDir(): String