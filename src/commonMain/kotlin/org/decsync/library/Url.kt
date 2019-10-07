/**
 * libdecsync - Url.kt
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

object Url {
    private val safeChars: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9') + "-_.~".toList()
    private val hexArray: List<Char> = ('0'..'9') + ('A'..'F')

    @ExperimentalStdlibApi
    fun encode(input: String): String {
        val output = input.encodeToByteArray().joinToString("") { byte ->
            val char = byte.toChar()
            if (char in safeChars) {
                val charArray = CharArray(1)
                charArray[0] = char
                String(charArray)
            } else {
                val i = byte.toInt()
                val charArray = CharArray(3)
                charArray[0] = '%'
                charArray[1] = hexArray[(i ushr 4) and 0x0F]
                charArray[2] = hexArray[i and 0x0F]
                String(charArray)
            }
        }

        return if (output.isNotEmpty() && output[0] == '.') {
            "%2E" + output.substring(1)
        } else {
            output
        }
    }

    fun decode(input: String): String? {
        val builder = StringBuilder()
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c != '%') {
                builder.append(c)
            } else {
                if (i + 2 >= input.length) return null
                val value = try {
                    (input[i + 1].toString() + input[i + 2].toString()).toInt(16)
                } catch (e: NumberFormatException) {
                    return null
                }
                builder.append(value.toChar())
                i += 2
            }
            i++
        }
        return builder.toString()
    }
}