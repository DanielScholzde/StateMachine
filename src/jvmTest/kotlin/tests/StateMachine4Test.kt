package tests

import common.AbstractLoggingStateMachine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue


open class StateMachine4 : AbstractLoggingStateMachine<Event, Boolean>() {

    suspend fun CoroutineScope.start() = start(::a)

    protected suspend fun a() {
        goto(::b)
    }

    protected open suspend fun b() {
        exitWithResult(false)
    }
}

class StateMachine4_1 : StateMachine4() {

    override suspend fun b() {
        goto(::c)
    }

    private suspend fun c() {
        exitWithResult(true)
    }
}


class StateMachine4Test {

    @Test
    fun test(): Unit = runBlocking {

        with(StateMachine4_1()) {

            val result = start()

            assertTrue(result.await())
        }
    }
}