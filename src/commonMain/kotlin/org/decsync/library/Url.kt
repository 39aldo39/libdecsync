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

@ExperimentalStdlibApi
object Url {
    private val safeChars: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9') + "-_.~".toList()
    private val hexArray: List<Char> = ('0'..'9') + ('A'..'F')

    fun encode(input: String): String {
        val output = input.encodeToByteArray().joinToString("") { byte ->
            val char = byte.toChar()
            if (char in safeChars) {
                val charArray = CharArray(1)
                charArray[0] = char
                charArray.concatToString()
            } else {
                val i = byte.toInt()
                val charArray = CharArray(3)
                charArray[0] = '%'
                charArray[1] = hexArray[(i ushr 4) and 0x0F]
                charArray[2] = hexArray[i and 0x0F]
                charArray.concatToString()
            }
        }

        return removeDot(output)
    }

    fun decode(inputWithoutDot: String): String? {
        if (inputWithoutDot.startsWith(".")) return null
        val input = addDot(inputWithoutDot)
        val bytes = ByteArray(input.length)
        var i = 0
        var j = 0
        while (i < input.length) {
            when (val c = input[i]) {
                '%' -> {
                    if (i + 2 >= input.length) return null
                    val index1 = hexArray.indexOf(input[i + 1]).takeIf { it >= 0 } ?: return null
                    val index2 = hexArray.indexOf(input[i + 2]).takeIf { it >= 0 } ?: return null
                    val value = 16 * index1 + index2
                    bytes[j++] = value.toByte()
                    i += 2
                }
                in safeChars -> bytes[j++] = c.toByte()
                else -> return null
            }
            i++
        }
        return byteArrayToString(bytes.copyOf(j))
    }

    private fun removeDot(input: String): String {
        return if (input.startsWith(".")) {
            "%2E" + input.substring(1)
        } else {
            input
        }
    }

    private fun addDot(input: String): String {
        return if (input.startsWith("%2E")) {
            "." + input.substring(3)
        } else {
            input
        }
    }
}