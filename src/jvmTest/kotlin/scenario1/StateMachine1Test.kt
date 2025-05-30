package scenario1

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals


class StateMachine1Test {

    @Test
    fun test() = runBlocking {
        with(StateMachine1()) {
            val result = start()

            sendEvent(Event.B) // transition to 'b' should be triggered; sendEvent() waits until event is processed
            sendEvent(Event.C(finish = false)) // transition to 'c' should be triggered
            sendEvent(Event.B)
            sendEvent(Event.C(finish = true)) // transition to end state 'endState' should be triggered

            assertEquals(Result.SUCCESS, result.await())
        }

        // output is:
        // pushed event: B
        // entering state function: a
        // executing transition to c
        // entering state function: c
        // received event: B
        // entering state function: b
        // pushed event: C(finish=false)
        // received event: C(finish=false)
        // entering state function: c
        // pushed event: B
        // received event: B
        // entering state function: b
        // pushed event: C(finish=true)
        // received event: C(finish=true)
        // entering state function: endState
    }

}