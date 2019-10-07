/**
 * libdecsync - Log.kt
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

private const val TAG = "DecSync"

actual object Log {
    actual fun e(message: String) { android.util.Log.e(TAG, message) }
    actual fun w(message: String) { android.util.Log.w(TAG, message) }
    actual fun i(message: String) { android.util.Log.i(TAG, message) }
    actual fun d(message: String) { android.util.Log.d(TAG, message) }
}