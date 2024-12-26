package de.danielscholz.statemachine

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration


suspend fun parallel(vararg functions: suspend () -> Unit) {
    coroutineScope { // use coroutineScope to cancel all parallel executed functions when leaving this state function
        functions.forEach {
            launch { it() }
        }
    }
}

suspend fun repeatEvery(interval: Duration, function: suspend () -> Unit) {
    while (true) {
        function()
        delay(interval)
    }
}
