# StateMachine

A tiny but powerful state machine powered by Kotlin Coroutines.

Advantages:

- state machine is non-blocking
- whole definition of states and transitions are done via well known Kotlin Coroutines code (class with suspend methods) with full IDE support
- execution can be limited to a single thread to prevent stale or inconsistent data (Coroutines feature; in most cases no 'volatile' and/or synchronization is needed)
- tiny implementation, only about 200 lines of code

For basic usage see simple example [StateMachine1](src/jvmTest/kotlin/scenario1/StateMachine1.kt) and [StateMachine1 test](src/jvmTest/kotlin/scenario1/StateMachine1Test.kt).

And for a more complex example see [StateMachine2](src/jvmTest/kotlin/scenario2/StateMachine2.kt) and [StateMachine2 test](src/jvmTest/kotlin/scenario2/StateMachine2Test.kt).