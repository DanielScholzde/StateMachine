package de.danielscholz.statemachine.intern

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock


internal class Barrier() { // an instance of this class should be used only one time!

    private val mutex = Mutex(locked = true)

    suspend fun wait() {
        mutex.withLock {}
    }

    fun release() {
        mutex.unlock()
    }
}