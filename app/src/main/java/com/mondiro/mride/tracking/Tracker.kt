package com.mondiro.mride.tracking

import kotlinx.coroutines.flow.MutableStateFlow

object Tracker {
    data class State(
        val recording: Boolean = false,
        val tripId: Long = 0,
        val startTime: Long = 0,
        val distanceM: Double = 0.0,
        val speedKmh: Float = 0f,
        val auto: Boolean = false,
    )

    val state = MutableStateFlow(State())
}
