package org.decsync.library

private const val TAG = "DecSync"

actual object Log {
    actual fun e(message: String) = println("$TAG: $message")
    actual fun w(message: String) = println("$TAG: $message")
    actual fun i(message: String) = println("$TAG: $message")
    actual fun d(message: String) = println("$TAG: $message")
}