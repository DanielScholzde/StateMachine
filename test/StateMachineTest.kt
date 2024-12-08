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
        // MyThread, 17.813: goto state function: b
        // MyThread, 18.708: pushed event: B
        // MyThread, 18.710: received event: B
        // MyThread, 18.720: pushed event: A
        // MyThread, 18.721: received event: A
        // MyThread, 18.723: goto state function: a
        // MyThread, 18.824: goto state function: b
        // MyThread, 19.721: pushed event: C(active=true)
        // MyThread, 19.723: received event: C(active=true)
        // MyThread, 19.724: goto state function: c
        // MyThread, 20.294: goto state function: a
        // MyThread, 20.396: goto state function: b
        // MyThread, 20.723: pushed event: D
        // MyThread, 20.724: received event: D
        // MyThread, 20.726: goto state function: d
        // MyThread, 21.735: goto state function: b
        //
    }
}