@file:OptIn(ExperimentalCoroutinesApi::class)

package scenario2

import de.danielscholz.statemachine.newSingleThreadDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds


class StateMachine2Test {

    @Test
    fun test() {
        newSingleThreadDispatcher.use { singleThreadDispatcher ->
            runBlocking(singleThreadDispatcher) {

                with(StateMachine2()) { // state machine is configured with clearEventsBeforeStateFunctionEnter = true

                    val stateMachineResult = async { runWaiting() }

                    delay(1000.milliseconds)
                    // here: state machine should be in state function 'b'
                    pushEvent(Event.B) // should have no effect; pushEvent() sends event to state machine and does not wait until it gets processed (unlike sendEvent)
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

                    stateMachineResult.cancel() // cancel state machine; otherwise it would run forever (has no end states)
                }

                // output is:
                // [MyThread @coroutine#3 ] 00.029: entering state function: a
                // [MyThread @coroutine#5 ] 00.189: executing transition to b
                // [MyThread @coroutine#5 ] 00.190: entering state function: b
                // [MyThread @coroutine#1 ] 01.037: pushed event: B
                // [MyThread @coroutine#6 ] 01.040: received event: B
                // [MyThread @coroutine#1 ] 01.051: pushed event: A
                // [MyThread @coroutine#6 ] 01.051: received event: A
                // [MyThread @coroutine#7 ] 01.053: entering state function: a
                // [MyThread @coroutine#9 ] 01.165: executing transition to b
                // [MyThread @coroutine#9 ] 01.165: entering state function: b
                // [MyThread @coroutine#1 ] 02.063: pushed event: C(active=true)
                // [MyThread @coroutine#10] 02.065: received event: C(active=true)
                // [MyThread @coroutine#11] 02.074: entering state function: c
                // [MyThread @coroutine#15] 02.600: entering state function: a
                // [MyThread @coroutine#17] 02.718: executing transition to b
                // [MyThread @coroutine#17] 02.719: entering state function: b
                // [MyThread @coroutine#1 ] 03.073: pushed event: D
                // [MyThread @coroutine#18] 03.074: received event: D
                // [MyThread @coroutine#19] 03.080: entering state function: d
                // [MyThread @coroutine#21] 04.183: entering state function: b
            }
        }
    }
}