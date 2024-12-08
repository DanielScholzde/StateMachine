package de.danielscholz.statemachine

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KSuspendFunction0
import kotlin.time.Duration

typealias StateFunction = KSuspendFunction0<Unit>


abstract class AbstractStateMachine<EVENT : Any>(dispatcher: CoroutineDispatcher) {

    private class LeaveStateFunctionException : Exception("leave state function")

    private val leaveStateFunctionException = LeaveStateFunctionException()
    private val context = CoroutineScope(dispatcher)
    private val stateExecutionMutex = Mutex()
    private val transitionLaunchCounter = AtomicInteger()
    private val events = MutableSharedFlow<EVENT>(extraBufferCapacity = 100)


    protected fun start(stateFunction: StateFunction) {
        launchGoto(stateFunction)
    }

    open fun stop() {
        context.cancel()
    }

    fun pushEvent(event: EVENT) {
        println("${getLogInfos()} pushed event: $event")
        this.events.tryEmit(event)
    }

    protected fun goto(stateFunction: StateFunction) {
        if (transitionLaunchCounter.incrementAndGet() == 1) {
            launchGoto(stateFunction)
        }
        throw leaveStateFunctionException // to leave current state function (cancel all pending code)
    }

    protected suspend fun consumeEvents(processEvent: suspend (EVENT) -> Unit) {
        events.collect { event ->
            println("${getLogInfos()} received event: $event")
            processEvent(event)
        }
    }

    protected suspend fun parallel(vararg functions: suspend () -> Unit) {
        coroutineScope {
            functions.forEach {
                launch { it() }
            }
        }
    }

    protected suspend fun repeatEvery(interval: Duration, function: suspend () -> Unit) {
        while (true) {
            function()
            delay(interval)
        }
    }


    private fun launchGoto(stateFunction: StateFunction) {
        context.launch {
            try {
                executeState(stateFunction)
            } catch (e: Exception) {
                if (e !is LeaveStateFunctionException) throw e
            }
        }
    }

    private suspend fun executeState(stateFunction: StateFunction) {
        stateExecutionMutex.withLock {
            println("${getLogInfos()} goto state function: ${stateFunction.name}")
            transitionLaunchCounter.set(0)
            stateFunction()
        }
    }

    private fun getLogInfos() =
        "${Thread.currentThread().name}, ${System.currentTimeMillis().let { ((it / 1000) % 60).toString().padStart(2, '0') + "." + (it % 1000).toString().padStart(3, '0') }}:"

}