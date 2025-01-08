package common

import de.danielscholz.statemachine.AbstractStateMachine
import de.danielscholz.statemachine.StateFunction


abstract class AbstractLoggingStateMachine<EVENT : Any, RESULT>(clearEventsBeforeStateFunctionEnter: Boolean = false) :
    AbstractStateMachine<EVENT, RESULT>(clearEventsBeforeStateFunctionEnter) {


    override fun onSendingEvent(event: EVENT, waitForProcessed: Boolean) {
        println("${getLogInfos()} push event to queue: $event")
    }

    override fun onSendEvent(event: EVENT, waitForProcessed: Boolean) {
        println("${getLogInfos()} pushed event to queue: $event")
    }

    override fun onEventReceived(event: EVENT, eventMeta: EventMeta, ignored: Boolean) {
        println("${getLogInfos()} received ${if (ignored) "ignored " else ""}event: $event")
    }

    override suspend fun onEnterState(stateFunction: StateFunction) {
        println("${getLogInfos()} entering state function: ${stateFunction.name}")
    }

    private val start = System.currentTimeMillis()

    protected open fun getLogInfos() =
        "[${Thread.currentThread().name.padEnd(22, ' ')}] ${
            (System.currentTimeMillis() - start).let { ((it / 1000) % 60).toString().padStart(2, '0') + "." + (it % 1000).toString().padStart(3, '0') }
        }: "

}
