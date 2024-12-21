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
    private val eventChannel = Channel<EventWrapper<EVENT>>(capacity = 100)

    private class EventWrapper<EVENT>(val event: EVENT, enableMutex: Boolean) {
        var processed = false
        var mutex = if (enableMutex) Mutex(locked = true) else null
    }


    protected fun start(stateFunction: StateFunction) {
        launchStateFunction(stateFunction)
    }

    open fun stop() {
        context.cancel()
        eventChannel.close()
    }

    /**
     * Add an event to the state machines event queue. Returns immediately.
     */
    fun pushEvent(event: EVENT) {
        log.info("${getLogInfos()} pushed event: $event")
        val result = eventChannel.trySend(EventWrapper(event, false))
        if (result.isFailure) log.error("Too many events and processing of events is to slow!")
        if (result.isClosed) log.error("Event-channel is already closed!")
    }

    suspend fun pushEventWait(event: EVENT): Boolean {
        log.info("${getLogInfos()} pushed event: $event")
        val eventWrapper = EventWrapper(event, true)
        eventChannel.send(eventWrapper)
        eventWrapper.mutex!!.withLock {} // wait until event is processed
        return eventWrapper.processed
    }

    protected fun goto(stateFunction: StateFunction, transitionAction: (suspend () -> Unit)? = null): Nothing {
        if (transitionsLaunchedCounter.incrementAndGet() == 1) {
            launchStateFunction(stateFunction, transitionAction)
        }
        throw leaveStateFunctionException // to leave current state function (cancel all pending code)
    }

    protected suspend fun consumeEvents(processEvent: suspend (EVENT) -> Unit) {
        for (eventWrapper in eventChannel) {
            log.info("${getLogInfos()} received event: ${eventWrapper.event}")
            processEvent(eventWrapper.event)
            eventWrapper.processed = true
            eventWrapper.mutex?.unlock()
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

    protected open suspend fun onEnterState(stateFunction: StateFunction) {  // can be overridden
        log.info("${getLogInfos()} entering state function: ${stateFunction.name}")
    }

    protected open suspend fun onExitState(stateFunction: StateFunction) {  // can be overridden
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
        fun clearEventsChannel() {
            while (true) {
                val result = eventChannel.tryReceive()
                if (!result.isSuccess) break
                result.getOrNull()?.mutex?.unlock()
            }
        }

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
                clearEventsChannel()
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
            } finally {
                clearEventsChannel()
            }
        }
    }

    private fun getLogInfos() =
        "${System.currentTimeMillis().let { ((it / 1000) % 60).toString().padStart(2, '0') + "." + (it % 1000).toString().padStart(3, '0') }}: "

}