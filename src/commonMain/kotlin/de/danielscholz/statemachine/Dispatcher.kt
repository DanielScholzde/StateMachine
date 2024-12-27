package de.danielscholz.statemachine

import kotlinx.coroutines.CloseableCoroutineDispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext


@OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
val newSingleThreadDispatcher: CloseableCoroutineDispatcher by lazy { newSingleThreadContext("MyThread") }
