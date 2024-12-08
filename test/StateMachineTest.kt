import de.danielscholz.statemachine.singleThreadDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds


class StateMachineTest {

    @Test
    fun test() = runBlocking(singleThreadDispatcher) {
        with(StateMachine()) {
            start()

            delay(1000.milliseconds)
            // here: state machine should be in state function 'b'
            pushEvent(Event.B) // should have no effect
            delay(10.milliseconds)
            pushEvent(Event.A) // transition to 'a' should be triggered

            delay(1000.milliseconds)
            // here: state machine should be in state function 'b'
            pushEvent(Event.C(active = true))

            delay(1000.milliseconds)
            // here: state machine should be in state function 'b'
            pushEvent(Event.D)

            delay(1000.milliseconds)
            // here: state machine should be in state function 'd'
            sensorValue = 0.6 // update sensor value

            delay(1000.milliseconds)
            // here: state machine should be in state function 'b'

            stop()
        }

        // output is:
        // MyThread - 38.717:  entering state function: a
        // MyThread - 38.822:  leaving state function: a
        // MyThread - 38.823:  entering state function: b
        // MyThread - 39.716:  pushed event: B
        // MyThread - 39.717:  received event: B
        // MyThread - 39.727:  pushed event: A
        // MyThread - 39.727:  received event: A
        // MyThread - 39.727:  leaving state function: b
        // MyThread - 39.727:  entering state function: a
        // MyThread - 39.828:  leaving state function: a
        // MyThread - 39.828:  entering state function: b
        // MyThread - 40.727:  pushed event: C(active=true)
        // MyThread - 40.728:  received event: C(active=true)
        // MyThread - 40.729:  leaving state function: b
        // MyThread - 40.729:  entering state function: c
        // MyThread - 41.270:  leaving state function: c
        // MyThread - 41.271:  entering state function: a
        // MyThread - 41.372:  leaving state function: a
        // MyThread - 41.373:  entering state function: b
        // MyThread - 41.728:  pushed event: D
        // MyThread - 41.729:  received event: D
        // MyThread - 41.730:  leaving state function: b
        // MyThread - 41.730:  entering state function: d
        // MyThread - 42.738:  leaving state function: d
        // MyThread - 42.738:  entering state function: b
    }
}