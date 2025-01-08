package tests

sealed class Event {
    data object A : Event() // no event parameter
    data object B : Event() // no event parameter
    data class C(val finish: Boolean) : Event() // event with a single parameter
}