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
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.reflect.KSuspendFunction0
import kotlin.time.Duration

typealias StateFunction = KSuspendFunction0<Unit>


abstract class AbstractStateMachine<EVENT : Any>(dispatcher: CoroutineDispatcher) {

    protected val log: Logger = LoggerFactory.getLogger(this::class.java)

    private class LeaveStateFunctionException : Exception("leave state function")

    private val leaveStateFunctionException = LeaveStateFunctionException()
    private val context = CoroutineScope(dispatcher)
    private val stateFunctionExecutionMutex = Mutex()
    private val transitionsLaunchedCounter = AtomicInteger()
    private val events = MutableSharedFlow<EVENT>(extraBufferCapacity = 100)


    protected fun start(stateFunction: StateFunction) {
        launchStateFunction(stateFunction)
    }

    open fun stop() {
        context.cancel()
    }

    fun pushEvent(event: EVENT) {
        log.info("${getLogInfos()} pushed event: $event")
        if (!events.tryEmit(event)) {
            log.error("Too many events and processing of events is to slow!")
        }
    }

    protected fun goto(stateFunction: StateFunction) {
        if (transitionsLaunchedCounter.incrementAndGet() == 1) {
            launchStateFunction(stateFunction)
        }
        throw leaveStateFunctionException // to leave current state function (cancel all pending code)
    }

    protected suspend fun consumeEvents(processEvent: suspend (EVENT) -> Unit) {
        events.collect { event ->
            log.info("${getLogInfos()} received event: $event")
            processEvent(event)
        }
    }

    protected suspend fun parallel(vararg functions: suspend () -> Unit) {
        coroutineScope { // use coroutineScope to cancel all parallel executed functions when leaving this state function
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

    protected open fun onEnterState(stateFunction: StateFunction) {  // can be overridden
        log.info("${getLogInfos()} entering state function: ${stateFunction.name}")
    }

    protected open fun onExitState(stateFunction: StateFunction) {  // can be overridden
        log.info("${getLogInfos()} leaving state function: ${stateFunction.name}")
    }

    protected open fun onExitStateFailure(stateFunction: StateFunction, e: Exception) {  // can be overridden
        log.info("${getLogInfos()} leaving state function with an exception: ${stateFunction.name}, ${e::class.simpleName}: ${e.message}")
    }

    protected open fun handleException(exception: Exception) {
        log.info("${getLogInfos()} exception occurred: $exception")
        throw exception
    }


    private fun launchStateFunction(stateFunction: StateFunction) {
        context.launch { executeStateFunction(stateFunction) }
    }

    private suspend fun executeStateFunction(stateFunction: StateFunction) {
        stateFunctionExecutionMutex.withLock {
            try {
                transitionsLaunchedCounter.set(0)
                onEnterState(stateFunction)
                stateFunction()
                onExitState(stateFunction) // this line should never be reached (all state functions must be exited via an exception)
            } catch (e: LeaveStateFunctionException) {
                onExitState(stateFunction)
            } catch (e: CancellationException) {
                throw e // CancellationException must always be re-thrown!
            } catch (e: Exception) {
                onExitStateFailure(stateFunction, e)
                handleException(e)
            }
        }
    }

    private fun getLogInfos() =
        "${System.currentTimeMillis().let { ((it / 1000) % 60).toString().padStart(2, '0') + "." + (it % 1000).toString().padStart(3, '0') }}: "

}