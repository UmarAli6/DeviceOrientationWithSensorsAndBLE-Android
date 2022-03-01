package com.example.lab3.filter

class AccFilter {

    private var currentValue: Float = 0.0f
    private var previousValue: Float = 0.0f
    private var filterFactor: Float = 0.1f

    fun filter(sensorValue: Float): Float {
        currentValue = filterFactor * previousValue + (1.0f - filterFactor) * sensorValue
        previousValue = currentValue
        return currentValue
    }

}