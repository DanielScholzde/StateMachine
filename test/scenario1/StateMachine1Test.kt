package scenario1

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test


class StateMachine1Test {

    @Test
    fun test1() = runBlocking {
        with(StateMachine1()) {
            val result = async { runWaiting() }

            pushEvent(Event.B) // transition to 'b' should be triggered
            pushEvent(Event.C(finish = false)) // transition to 'c' should be triggered
            pushEvent(Event.B)
            pushEvent(Event.C(finish = true)) // transition to end state 'endState' should be triggered

            println(result.await())
        }

        // output is:
        // pushed event: B
        // pushed event: C(finish=false)
        // pushed event: B
        // pushed event: C(finish=true)
        // entering state function: a
        // executing transition to c
        // entering state function: c
        // received event: B
        // entering state function: b
        // received event: C(finish=false)
        // entering state function: c
        // received event: B
        // entering state function: b
        // received event: C(finish=true)
        // entering state function: endState
        // SUCCESS
    }

    @Test
    fun test2() = runBlocking {
        with(StateMachine1()) {
            val result = async { runWaiting() }

            pushEventWait(Event.B) // transition to 'b' should be triggered
            pushEventWait(Event.C(finish = false)) // transition to 'c' should be triggered
            pushEventWait(Event.B)
            pushEventWait(Event.C(finish = true)) // transition to end state 'endState' should be triggered

            println(result.await())
        }

        // output is:
        // pushed event: B
        // entering state function: a
        // executing transition to c
        // entering state function: c
        // received event: B
        // pushed event: C(finish=false)
        // entering state function: b
        // received event: C(finish=false)
        // pushed event: B
        // entering state function: c
        // received event: B
        // pushed event: C(finish=true)
        // entering state function: b
        // received event: C(finish=true)
        // entering state function: endState
        // SUCCESS
    }
}