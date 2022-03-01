package com.example.lab3.filter

import android.util.Log

class CombFilter {

    private var currentValue: Float = 0.0f
    private var previousValue: Float = 0.0f
    private var filterFactor: Float = 0.1f

    fun filter(gyroYValue: Float, dT: Long, accPitch: Float): Float {
        val currentTime = System.currentTimeMillis()
        currentValue =
            (1.0f - filterFactor) * (previousValue + dT * gyroYValue) + (filterFactor * accPitch)
        previousValue = currentValue

        return currentValue
    }
}