package scenario1

import common.AbstractLoggingStateMachine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds


sealed class Event {
    data object A : Event() // no event parameter
    data object B : Event() // no event parameter
    data class C(val finish: Boolean) : Event() // event with a single parameter
}

enum class Result { SUCCESS, FAILURE }


// state machine with an end state and an events channel which keeps all events until they get processed
class StateMachine1 : AbstractLoggingStateMachine<Event, Result>() {

    suspend fun CoroutineScope.start() = start(::a) // specify start state function 'a'

    private suspend fun a() {
        try {
            // do initial work for state here
            delay(100.milliseconds)
            goto(::c) {
                println("executing transition to c")
            }
        } finally {
            // do cleanup work for state here
        }
    }

    private suspend fun b() {
        consumeEvents { event ->
            when {
                event is Event.A -> goto(::a)
                // Event.B is ignored
                event is Event.C && event.finish -> goto(::endState)
                event is Event.C -> goto(::c)
            }
        }
    }

    private suspend fun c() {
        consumeEvents { event ->
            when {
                event is Event.A -> goto(::a)
                event is Event.B -> goto(::b)
            }
        }
    }

    private suspend fun endState() {
        exitWithResult(Result.SUCCESS)
    }

    override fun handleException(exception: Exception) {
        exitWithResult(Result.FAILURE)
    }

    override fun getLogInfos() = ""
}