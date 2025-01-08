package de.danielscholz.statemachine

import de.danielscholz.statemachine.intern.Barrier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.cancellation.CancellationException
import kotlin.reflect.KSuspendFunction0
import kotlin.time.Duration
import kotlin.time.TimeSource
import kotlin.time.TimeSource.Monotonic.ValueTimeMark
import kotlin.time.measureTime

typealias StateFunction = KSuspendFunction0<Unit>
typealias TransitionAction = suspend () -> Unit

// Copyright (c) 2024 Daniel Scholz

/**
 * Base for a custom non-blocking state machine.
 *
 * This basic implementation guarantees that a maximum of one state function or transition action is active at any time.
 */
abstract class AbstractStateMachine<EVENT : Any, RESULT : Any?>(val clearEventsBeforeStateFunctionEnter: Boolean = false) {

    private class LeaveStateFunctionException : Exception("leave state function")
    private class ExitStateMachineException : Exception("exit state machine")

    private val leaveStateFunctionException = LeaveStateFunctionException() // pre-instantiated exception for better performance
    private var coroutineScope: CoroutineScope? = null // CoroutineScope of complete state machine
    private var stateFunctionCoroutineScope: CoroutineScope? = null // CoroutineScope of a single state function
    private var eventConsumer: Job? = null
    private var result: RESULT? = null
    private val stateFunctionExecutionMutex = Mutex() // ensures, that only one state function is executed at any time
    private val transitionsLaunchedCounter = AtomicInteger()
    private val counter = AtomicLong() // increments before each transition and each state function enter
    private val eventChannel = Channel<EventWrapper<EVENT>>(capacity = UNLIMITED)
    private var startupCompleteBarrier: Barrier? = Barrier()
    private var currentEvent: EventWrapper<EVENT>? = null

    @Volatile
    protected var currentState: StateFunction? = null
        private set

    private class EventWrapper<EVENT>(val event: EVENT, val createdOnCount: Long, enableWaitForProcessed: Boolean) {
        val created = TimeSource.Monotonic.markNow()
        private var processed = false
        private var hasTriggeredAStateChange = false
        private val eventProcessedBarrier = if (enableWaitForProcessed) Barrier() else null

        suspend fun waitForEventProcessedResult(): SendEventResult {
            eventProcessedBarrier!!.wait()
            return SendEventResult(processed, hasTriggeredAStateChange)
        }

        fun eventReceived(processed: Boolean, hasTriggeredAStateChange: Boolean) {
            this.processed = processed
            this.hasTriggeredAStateChange = hasTriggeredAStateChange
            eventProcessedBarrier?.release()
        }
    }

    class EventMeta(val created: ValueTimeMark, val createdWithinThisStateFunction: Boolean)


    protected suspend fun CoroutineScope.start(startStateFunction: StateFunction): Deferred<RESULT> {
        val startupComplete = startupCompleteBarrier!!
        val deferredResult = async {
            if (coroutineScope != null) throw IllegalStateException("State machine is already running!")
            try {
                coroutineScope {
                    coroutineScope = this
                    launchStateFunction(startStateFunction)
                    // coroutineScope waits, until all child coroutines (state functions) are finished
                }
                @Suppress("UNCHECKED_CAST")
                result as RESULT
            } finally {
                coroutineScope = null
                cleanup()
                startupCompleteBarrier?.release() // release barrier if some exception happened and start state could not be reached
            }
        }
        startupComplete.wait()
        return deferredResult
    }

    protected open fun cleanup() {
        eventChannel.close()
    }

    /** Add an event to the state machines event queue. Returns immediately. */
    fun pushEvent(event: EVENT) {
        onSendingEvent(event, waitForProcessed = false)
        val result = eventChannel.trySend(EventWrapper(event, counter.get(), enableWaitForProcessed = false))
        onSendEvent(event, waitForProcessed = false)
        if (result.isFailure) throw IllegalStateException()
        if (result.isClosed) throw IllegalStateException("Event channel is already closed!")
    }

    suspend fun sendEvent(event: EVENT): SendEventResult {
        onSendingEvent(event, waitForProcessed = true)
        val eventWrapper = EventWrapper(event, counter.get(), enableWaitForProcessed = true)
        eventChannel.send(eventWrapper) // throws an exception if channel is closed
        onSendEvent(event, waitForProcessed = true)
        return eventWrapper.waitForEventProcessedResult() // wait until event is processed
    }

    data class SendEventResult(val processed: Boolean, val hasTriggeredAStateChange: Boolean)


    protected fun goto(stateFunction: StateFunction, transitionAction: TransitionAction? = null): Nothing {
        if (transitionsLaunchedCounter.incrementAndGet() == 1) {
            launchStateFunction(stateFunction, transitionAction, currentEvent)
        }
        throw leaveStateFunctionException // to leave current state function (cancel all pending coroutines/code)
    }

    private fun launchStateFunction(stateFunction: StateFunction, transitionAction: TransitionAction? = null, currentEvent: EventWrapper<EVENT>? = null) {
        coroutineScope!!.launch { executeStateFunction(stateFunction, transitionAction, currentEvent) }
    }

    /** HINT: this method keeps running within state function and stops at exiting or when ignoreEvents is called */
    protected suspend fun consumeEventsWithMeta(processEvent: suspend (EVENT, EventMeta) -> Unit): Nothing {
        eventConsumer?.cancel() // stop/cancel existing event consumer
        val job = stateFunctionCoroutineScope!!.launch {
            for (eventWrapper in eventChannel) {
                val eventMeta = EventMeta(eventWrapper.created, eventWrapper.createdOnCount == counter.get())
                onEventReceived(eventWrapper.event, eventMeta, ignored = false)
                try {
                    currentEvent = eventWrapper // set currentEvent to be picked up by goto method
                    processEvent(eventWrapper.event, eventMeta)
                    eventWrapper.eventReceived(processed = true, hasTriggeredAStateChange = false)
                } catch (e: LeaveStateFunctionException) {
                    // eventReceived is called deferred within executeStateFunction
                    throw e
                } catch (e: ExitStateMachineException) {
                    eventWrapper.eventReceived(processed = true, hasTriggeredAStateChange = true)
                    throw e
                } catch (e: Exception) {
                    eventWrapper.eventReceived(processed = true, hasTriggeredAStateChange = false)
                    throw e
                } finally {
                    currentEvent = null
                }
            }
        }
        eventConsumer = job
        job.join() // should always throw a CancellationException
        throw IllegalStateException() // this line should never be reached
    }

    protected suspend fun consumeEvents(processEvent: suspend (EVENT) -> Unit): Nothing {
        consumeEventsWithMeta { event, meta ->
            processEvent(event)
        }
    }

    /** HINT: this method does not wait; keeps running within state function and stops at exiting or when consumeEvents is called */
    protected fun ignoreEvents() {
        eventConsumer?.cancel() // stop/cancel existing event consumer
        eventConsumer = stateFunctionCoroutineScope!!.launch {
            for (eventWrapper in eventChannel) {
                onEventReceived(eventWrapper.event, EventMeta(eventWrapper.created, createdWithinThisStateFunction = eventWrapper.createdOnCount == counter.get()), ignored = true)
                eventWrapper.eventReceived(processed = false, hasTriggeredAStateChange = false)
            }
        }
    }

    protected fun clearEventsChannel() { // non-suspending!
        while (true) {
            val result = eventChannel.tryReceive()
            if (!result.isSuccess) break
            val eventWrapper = result.getOrNull()!!
            onEventReceived(eventWrapper.event, EventMeta(eventWrapper.created, createdWithinThisStateFunction = eventWrapper.createdOnCount == counter.get()), ignored = true)
            eventWrapper.eventReceived(processed = false, hasTriggeredAStateChange = false)
        }
    }

    protected fun exitWithResult(result: RESULT): Nothing {
        this.result = result
        throw ExitStateMachineException() // to leave current state function (cancel all pending coroutines/code)
    }


    /** ATTENTION: this method must never call goto() or exitWithResult() AND must never throw an exception! */
    protected open fun onSendingEvent(event: EVENT, waitForProcessed: Boolean) {}

    /** ATTENTION: this method must never call goto() or exitWithResult() AND must never throw an exception! */
    protected open fun onSendEvent(event: EVENT, waitForProcessed: Boolean) {}

    /** ATTENTION: this method must never call goto() or exitWithResult() AND must never throw an exception! */
    protected open fun onEventReceived(event: EVENT, eventMeta: EventMeta, ignored: Boolean) {}

    /** HINT: this method is allowed to call goto() or exitWithResult() */
    protected open suspend fun onEnterState(stateFunction: StateFunction) {}

    /** ATTENTION: this method must never call goto() or exitWithResult()! */
    protected open suspend fun onExitState(stateFunction: StateFunction) {}

    /** HINT: this method is allowed to call goto() or exitWithResult() */
    protected open fun onExitStateFunctionWithFailure(stateFunction: StateFunction, e: Exception) {}

    /** HINT: this method is allowed to call goto() or exitWithResult() */
    protected open fun handleException(exception: Exception): Unit = throw exception

    /** ATTENTION: this method must never call goto() or exitWithResult()! */
    protected open fun onTransition(fromState: StateFunction, toState: StateFunction) {}

    /** ATTENTION: this method must never call goto() or exitWithResult()! */
    protected open fun onTransitionActionFinished(fromState: StateFunction, toState: StateFunction, duration: Duration) {}


    /** ATTENTION: this method must never throw any exceptions except CancellationException! */
    private suspend fun executeStateFunction(stateFunction: StateFunction, transitionAction: TransitionAction?, event: EventWrapper<EVENT>?) {
        stateFunctionExecutionMutex.withLock {
            transitionsLaunchedCounter.set(0) // reset counter

            if (currentState != null) {
                onTransition(currentState!!, stateFunction)

                transitionAction?.let {
                    val ok = handleExceptionsIntern {
                        val duration = measureTime {
                            coroutineScope {
                                stateFunctionCoroutineScope = this
                                try {
                                    counter.incrementAndGet()
                                    transitionAction.invoke()
                                } finally {
                                    eventConsumer = null
                                    stateFunctionCoroutineScope = null
                                }
                            }
                        }
                        // 'transition' to start state function has no transitionAction, so here currentState must always be not null
                        onTransitionActionFinished(fromState = currentState!!, toState = stateFunction, duration = duration)
                    }
                    if (!ok) return@withLock
                }
            }

            currentState = stateFunction // do not set to null between two stateFunctions

            startupCompleteBarrier?.release() // release startup complete barrier after setting currentState
            startupCompleteBarrier = null

            event?.eventReceived(processed = true, hasTriggeredAStateChange = true) // release event processed barrier after executing transition and setting currentState

            handleExceptionsIntern(stateFunction) {
                coroutineScope {
                    stateFunctionCoroutineScope = this
                    try {
                        if (clearEventsBeforeStateFunctionEnter) clearEventsChannel()
                        counter.incrementAndGet()
                        onEnterState(stateFunction)
                        stateFunction()
                        onExitState(stateFunction) // this line should never be reached (call of goto() or exitWithResult() is missing)
                    } catch (e: LeaveStateFunctionException) {
                        onExitState(stateFunction)
                        throw e
                    } catch (e: ExitStateMachineException) {
                        onExitState(stateFunction)
                        throw e
                    } finally {
                        eventConsumer = null
                        stateFunctionCoroutineScope = null
                    }
                }
            }
        }
    }

    private suspend fun handleExceptionsIntern(stateFunction: StateFunction? = null, recursiveCall: Boolean = false, block: suspend () -> Unit): Boolean =
        try {
            block()
            true
        } catch (_: LeaveStateFunctionException) { // goto() was called
            false
        } catch (_: ExitStateMachineException) { // exitWithResult() was called
            false
        } catch (e: CancellationException) {
            throw e // CancellationException must always be re-thrown!
        } catch (e: Exception) {
            if (recursiveCall) throw e // no further recursive exception handling!
            handleExceptionsIntern(recursiveCall = true) {
                if (stateFunction != null) {
                    onExitStateFunctionWithFailure(stateFunction, e) // may only throw LeaveStateFunctionException, ExitStateMachineException, CancellationException!
                }
                handleException(e) // may only throw LeaveStateFunctionException, ExitStateMachineException, CancellationException!
            }
        }
}