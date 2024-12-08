package de.danielscholz.statemachine

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext


@OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
val singleThreadDispatcher by lazy { newSingleThreadContext("MyThread") }
