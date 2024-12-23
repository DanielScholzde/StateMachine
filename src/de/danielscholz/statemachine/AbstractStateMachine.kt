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
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.cancellation.CancellationException
import kotlin.reflect.KSuspendFunction0
import kotlin.time.Duration
import kotlin.time.TimeSource
import kotlin.time.TimeSource.Monotonic.ValueTimeMark

typealias StateFunction = KSuspendFunction0<Unit>
typealias TransitionAction = suspend () -> Unit

/**
 * Base for a custom non-blocking state machine.
 *
 * This base implementation guarantees that only one state function is active at any time and that only one transition is executed at any time.
 */
abstract class AbstractStateMachine<EVENT : Any>(dispatcher: CoroutineDispatcher, val clearEventsBeforeStateFunctionEnter: Boolean = false) {

    protected val log: Logger = LoggerFactory.getLogger(this::class.java)

    private class LeaveStateFunctionException : Exception("leave state function")

    private val leaveStateFunctionException = LeaveStateFunctionException()
    private val scope = CoroutineScope(dispatcher) // supervisorScope?
    private var stateFunctionScope: CoroutineScope? = null
    private val stateFunctionExecutionMutex = Mutex()
    private val transitionsLaunchedCounter = AtomicInteger()
    private val counter = AtomicLong() // increments before each transition and each state function enter
    private val eventChannel = Channel<EventWrapper<EVENT>>(capacity = 100)

    private class EventWrapper<EVENT>(val event: EVENT, val createdOnCount: Long, enableMutex: Boolean) {
        var processed = false
        val created = TimeSource.Monotonic.markNow()
        var mutex = if (enableMutex) Mutex(locked = true) else null
    }

    class EventMeta(val created: ValueTimeMark, val createdWithinThisStateFunction: Boolean)


    protected fun start(stateFunction: StateFunction) {
        launchStateFunction(stateFunction)
    }

    open fun stop() {
        scope.cancel()
        eventChannel.close()
    }

    /**
     * Add an event to the state machines event queue. Returns immediately.
     */
    fun pushEvent(event: EVENT) {
        log.info("${getLogInfos()} pushed event: $event")
        val result = eventChannel.trySend(EventWrapper(event, counter.get(), false))
        if (result.isFailure) log.error("Too many events and processing of events is to slow!")
        if (result.isClosed) log.error("Event-channel is already closed!")
    }

    suspend fun pushEventWait(event: EVENT): Boolean {
        log.info("${getLogInfos()} pushed event: $event")
        val eventWrapper = EventWrapper(event, counter.get(), true)
        eventChannel.send(eventWrapper)
        eventWrapper.mutex!!.withLock {} // wait until event is processed
        return eventWrapper.processed
    }

    protected fun goto(stateFunction: StateFunction, transitionAction: TransitionAction? = null): Nothing {
        if (transitionsLaunchedCounter.incrementAndGet() == 1) {
            launchStateFunction(stateFunction, transitionAction)
        }
        throw leaveStateFunctionException // to leave current state function (cancel all pending code)
    }

    protected suspend fun consumeEvents(processEvent: suspend (EVENT, EventMeta) -> Unit) {
        for (eventWrapper in eventChannel) {
            log.info("${getLogInfos()} received event: ${eventWrapper.event}")
            processEvent(eventWrapper.event, EventMeta(eventWrapper.created, eventWrapper.createdOnCount == counter.get()))
            eventWrapper.processed = true
            eventWrapper.mutex?.unlock()
        }
    }

    protected fun ignoreEvents() {
        stateFunctionScope!!.launch {
            for (eventWrapper in eventChannel) {
                log.info("${getLogInfos()} received event (ignored): ${eventWrapper.event}")
                eventWrapper.mutex?.unlock()
            }
        }
    }

    protected fun clearEventsChannel() { // non-suspending!
        while (true) {
            val result = eventChannel.tryReceive()
            if (!result.isSuccess) break
            result.getOrNull()?.mutex?.unlock()
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

    protected open fun onExitStateFunctionWithFailure(stateFunction: StateFunction, e: Exception) {  // can be overridden
        log.info("${getLogInfos()} leaving state function with an exception: ${stateFunction.name}, ${e::class.simpleName}: ${e.message}")
    }

    protected open fun handleException(exception: Exception) {
        log.info("${getLogInfos()} exception occurred: $exception")
        throw exception
    }


    private fun launchStateFunction(stateFunction: StateFunction, transitionAction: TransitionAction? = null) {
        scope.launch { executeStateFunction(stateFunction, transitionAction) }
    }

    private suspend fun executeStateFunction(stateFunction: StateFunction, transitionAction: TransitionAction?) {
        stateFunctionExecutionMutex.withLock {
            transitionsLaunchedCounter.set(0) // reset counter
            transitionAction?.let {
                try {
                    coroutineScope {
                        stateFunctionScope = this
                        try {
                            counter.incrementAndGet()
                            transitionAction.invoke()
                        } finally {
                            stateFunctionScope = null
                        }
                    }
                } catch (e: CancellationException) {
                    throw e // CancellationException must always be re-thrown!
                } catch (e: Exception) {
                    handleException(e)
                }
            }
            try {
                coroutineScope {
                    stateFunctionScope = this
                    try {
                        if (clearEventsBeforeStateFunctionEnter) clearEventsChannel()
                        counter.incrementAndGet()
                        onEnterState(stateFunction)
                        stateFunction()
                        // next lines should never be reached (all state functions must be exited via an exception)
                        onExitState(stateFunction)
                        log.error("State function ${stateFunction.name} has exited without any transition to an other state function!")
                    } finally {
                        stateFunctionScope = null
                    }
                }
            } catch (e: LeaveStateFunctionException) {
                onExitState(stateFunction)
            } catch (e: CancellationException) {
                throw e // CancellationException must always be re-thrown!
            } catch (e: Exception) {
                onExitStateFunctionWithFailure(stateFunction, e)
                handleException(e)
            }
        }
    }

    private fun getLogInfos() =
        "${System.currentTimeMillis().let { ((it / 1000) % 60).toString().padStart(2, '0') + "." + (it % 1000).toString().padStart(3, '0') }}: "

}