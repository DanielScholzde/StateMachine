package scenario2

import de.danielscholz.statemachine.AbstractStateMachine
import de.danielscholz.statemachine.StateFunction
import de.danielscholz.statemachine.parallel
import de.danielscholz.statemachine.repeatEvery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds


sealed class Event {
    data object A : Event() // no event parameter
    data object B : Event() // no event parameter
    data class C(val active: Boolean) : Event() // event with a single parameter
    data object D : Event() // no event parameter
}

var sensorValue = 0.0


// state machine with no end states (it never stops normally) and cleared events channel for each state function
class StateMachine2 : AbstractStateMachine<Event, Unit>(clearEventsBeforeStateFunctionEnter = true) {

    suspend fun CoroutineScope.start() = start(::a) // specify start state function 'a'

    private suspend fun a() {
        try {
            // do initial work for state here
            ignoreEvents() // ignore all events already happened and that occurs within this state function execution duration (this state has no event consumer)
            delay(100.milliseconds)
            goto(::b) {
                println("${getLogInfos()} executing transition to b")
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
                event is Event.C && event.active -> goto(::c)
                event is Event.D -> goto(::d)
            }
        }
    }

    private suspend fun c() {
        // do initial work for state here
        parallel(
            {
                consumeEvents { event ->
                    when {
                        event is Event.A -> goto(::a)
                        event is Event.C -> goto(::c)
                    }
                }
            },
            { // fallback, when no event occurred within 500ms
                delay(500.milliseconds)
                goto(::a)
            }
        )
    }

    private suspend fun d() {
        ignoreEvents()
        repeatEvery(100.milliseconds) {
            if (sensorValue > 0.5) goto(::b)
        }
    }


    override fun onEventPushed(event: Event, waitForProcessed: Boolean) {
        println("${getLogInfos()} pushed event: $event")
    }

    override fun onEventReceived(event: Event, eventMeta: EventMeta, ignored: Boolean) {
        println("${getLogInfos()} received ${if (ignored) "ignored " else ""}event: $event")
    }

    override suspend fun onEnterState(stateFunction: StateFunction) {
        println("${getLogInfos()} entering state function: ${stateFunction.name}")
    }

    val start = System.currentTimeMillis()

    private fun getLogInfos() =
        "[${Thread.currentThread().name.padEnd(22, ' ')}] ${
            (System.currentTimeMillis() - start).let { ((it / 1000) % 60).toString().padStart(2, '0') + "." + (it % 1000).toString().padStart(3, '0') }
        }:"

}
