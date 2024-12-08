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
    }
}