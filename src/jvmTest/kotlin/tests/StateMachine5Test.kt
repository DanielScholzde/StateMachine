package tests

import common.AbstractLoggingStateMachine
import de.danielscholz.statemachine.parallel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds


class StateMachine5Test {

    inner class StateMachine5 : AbstractLoggingStateMachine<Event, Boolean>() {

        suspend fun CoroutineScope.start() = start(::a)

        private suspend fun a() {
            parallel(
                {
                    consumeEvents {
                        if (it == Event.B) goto(::b)
                    }
                },
                {
                    delay(100.milliseconds)

                    methodWhichSendsAnEvent()
                }
            )
        }

        private suspend fun b() {
            exitWithResult(true)
        }
    }

    val stateMachine = StateMachine5()

    private suspend fun methodWhichSendsAnEvent() {
        stateMachine.sendEvent(Event.B)
    }

    @Test
    fun test(): Unit = runBlocking {

        with(stateMachine) {

            val result = start()

            assertTrue(result.await())
        }
    }
}