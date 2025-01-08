package tests

import common.AbstractLoggingStateMachine
import de.danielscholz.statemachine.parallel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds


class StateMachine3 : AbstractLoggingStateMachine<Event, Boolean>() {

    suspend fun CoroutineScope.start() = start(::a)

    private suspend fun a() {
        parallel(
            {
                consumeEvents {
                    // no call of goto method
                }
            },
            {
                println("${getLogInfos()} starting fallback count down")
                delay(200.milliseconds)
                println("${getLogInfos()} fallback count down finished")
                goto(::b) {
                    println("${getLogInfos()} executing transition to b")
                }
            }
        )
    }

    private suspend fun b() {
        ignoreEvents()
        delay(500.milliseconds)
        exitWithResult(true)
    }
}


class StateMachine3Test {

    @Test
    fun test(): Unit = runBlocking {

        with(StateMachine3()) {

            val result = start()
            // here: state machine should be in state function 'a'

            delay(400.milliseconds)
            // here: state machine should be in state function 'b'

            sendEvent(Event.A) // should be received and ignored

            assertTrue(result.await())
        }
    }
}