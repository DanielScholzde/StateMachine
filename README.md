# StateMachine

A tiny but powerful state machine powered by Kotlin Coroutines.

Advantages:

- state machine is non-blocking
- state machine can be run on a single thread to prevent stale data (in most cases no 'volatile' and/or synchronization needed)
- whole definition is done via well known Kotlin (Coroutines) code with full IDE support
- tiny implementation, only about 100 lines of code

For usage see example [StateMachine](test/StateMachine.kt) and [StateMachine test](test/StateMachineTest.kt).