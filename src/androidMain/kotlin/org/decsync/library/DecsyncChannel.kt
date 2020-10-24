package org.decsync.library

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.launch

/**
 * Represents a channel to a DecSync instance. Allows for executing asynchronous actions on it.
 */
@ExperimentalStdlibApi
@ObsoleteCoroutinesApi
abstract class DecsyncChannel<T, V> {
    /**
     * Function denoting whether DecSync is enabled. When this returns false, no actions are
     * executed on the DecSync instance.
     */
    abstract fun isDecsyncEnabled(context: V): Boolean

    /**
     * Returns a new DecSync instance, given the context [context].
     */
    abstract fun getNewDecsync(context: V): Decsync<T>

    /**
     * Handles an exception thrown by [getNewDecsync].
     */
    abstract fun onException(context: V, e: Exception)

    /**
     * Execute the action [action] on the DecSync instance in the background. If required, a new
     * DecSync instance is initialized with context [context].
     */
    fun withDecsync(context: V, action: Decsync<T>.() -> Unit) {
        GlobalScope.launch {
            decsyncChannel.send(Msg.Action(context, action))
        }
    }

    /**
     * Similar to [withDecsync], but re-initializes the DecSync instances. Useful when the DecSync
     * is first used and the DecSync directory might has changed.
     */
    fun initSyncWith(context: V, action: Decsync<T>.() -> Unit) {
        GlobalScope.launch {
            decsyncChannel.send(Msg.Reset())
            decsyncChannel.send(Msg.Action(context, action))
        }
    }

    private sealed class Msg<T, V> {
        class Reset<T, V> : Msg<T, V>()
        data class Action<T, V>(val context: V, val action: Decsync<T>.() -> Unit) : Msg<T, V>()
    }
    private val decsyncChannel = GlobalScope.actor<Msg<T, V>> {
        var mDecsync: Decsync<T>? = null
        for (msg in channel) {
            when (msg) {
                is Msg.Reset -> mDecsync = null
                is Msg.Action -> {
                    val context = msg.context
                    if (!isDecsyncEnabled(context)) continue
                    try {
                        val decsync = mDecsync ?: run {
                            getNewDecsync(context).also {
                                mDecsync = it
                            }
                        }
                        msg.action(decsync)
                    } catch (e: Exception) {
                        onException(context, e)
                    }
                }
            }
        }
    }
}