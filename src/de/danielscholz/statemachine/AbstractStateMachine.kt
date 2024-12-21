package de.danielscholz.statemachine

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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

/**
 * Base for a custom non-blocking state machine.
 *
 * This base implementation guarantees that only one state function is active at any time and that only one transition is executed at any time.
 */
abstract class AbstractStateMachine<EVENT : Any>(dispatcher: CoroutineDispatcher) {

    protected val log: Logger = LoggerFactory.getLogger(this::class.java)

    private class LeaveStateFunctionException : Exception("leave state function")

    private val leaveStateFunctionException = LeaveStateFunctionException()
    private val context = CoroutineScope(dispatcher) // supervisorScope?
    private val stateFunctionExecutionMutex = Mutex()
    private val transitionsLaunchedCounter = AtomicInteger()
    private val events = Channel<EVENT>(capacity = 100)


    protected fun start(stateFunction: StateFunction) {
        launchStateFunction(stateFunction)
    }

    open fun stop() {
        context.cancel()
    }

    /**
     * Add an event to the state machines event queue. Returns immediately.
     */
    fun pushEvent(event: EVENT) {
        log.info("${getLogInfos()} pushed event: $event")
        val result = events.trySend(event)
        if (result.isFailure || result.isClosed) {
            log.error("Too many events and processing of events is to slow!")
        }
    }

    protected fun goto(stateFunction: StateFunction, transitionAction: (suspend () -> Unit)? = null): Nothing {
        if (transitionsLaunchedCounter.incrementAndGet() == 1) {
            launchStateFunction(stateFunction, transitionAction)
        }
        throw leaveStateFunctionException // to leave current state function (cancel all pending code)
    }

    protected suspend fun consumeEvents(processEvent: suspend (EVENT) -> Unit) {
        for (event in events) {
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


    private fun launchStateFunction(stateFunction: StateFunction, transitionAction: (suspend () -> Unit)? = null) {
        context.launch { executeStateFunction(stateFunction, transitionAction) }
    }

    private suspend fun executeStateFunction(stateFunction: StateFunction, transitionAction: (suspend () -> Unit)?) {
        stateFunctionExecutionMutex.withLock {
            transitionsLaunchedCounter.set(0) // reset counter
            try {
                transitionAction?.invoke()
            } catch (e: CancellationException) {
                throw e // CancellationException must always be re-thrown!
            } catch (e: Exception) {
                handleException(e)
            }
            try {
                while (true) {
                    if (!events.tryReceive().isSuccess) break
                }
                onEnterState(stateFunction)
                stateFunction()
                // next lines should never be reached (all state functions must be exited via an exception)
                onExitState(stateFunction)
                log.error("State function ${stateFunction.name} has exited without any transition to an other state function!")
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