import de.danielscholz.statemachine.AbstractStateMachine
import de.danielscholz.statemachine.singleThreadDispatcher
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds


sealed class Event {
    data object A : Event() // no event parameter
    data object B : Event() // no event parameter
    data class C(val active: Boolean) : Event() // event with a single parameter
    data object D : Event() // no event parameter
}

var sensorValue = 0.0


class StateMachine : AbstractStateMachine<Event>(singleThreadDispatcher) {

    fun start() = start(::a) // specify start state function 'a'

    private suspend fun a() {
        // do initial work for state here
        delay(100.milliseconds)
        goto(::b) {
            log.info("executing transition to b")
        }
    }

    private suspend fun b() {
        consumeEvents { event ->
            when {
                event is Event.A -> goto(::a)
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
        repeatEvery(100.milliseconds) {
            if (sensorValue > 0.5) goto(::b)
        }
    }

}
