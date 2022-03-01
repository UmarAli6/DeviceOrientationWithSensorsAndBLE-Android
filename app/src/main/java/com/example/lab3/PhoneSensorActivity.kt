package com.example.lab3

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.util.Log
import android.widget.Button
import android.widget.TextView
import com.example.lab3.filter.CombFilter
import com.example.lab3.filter.AccFilter
import java.io.File
import kotlin.math.atan
import kotlin.math.round
import kotlin.math.sqrt

class PhoneSensorActivity : AppCompatActivity(), SensorEventListener {

    // UI
    private lateinit var mAccOnlyTextView: TextView
    private lateinit var mGyroAndAccTextView: TextView
    private lateinit var mRecordButton: Button
    private lateinit var mTimeButton: Button

    // sensor
    private lateinit var mSensorManager: SensorManager
    private lateinit var mAccSensor: Sensor
    private lateinit var mGyroSensor: Sensor

    // flags
    private var record = false
    private var onlyOnce = false
    private var onlyOnce2 = false

    // time
    private var timestamp = 0L
    private var startTime = 0L
    private var mStartTimestamp = 0L
    private var mStartTimestamp2 = 0L
    private var recordTime = 10000L

    // sensor values
    private var mAccX = 0.0f
    private var mAccY = 0.0f
    private var mAccZ = 0.0f
    private var mGyroY = 0.0f
    private var mAccPitch = 0.0f
    private var mCombPitch = 0.0f

    // filenames
    private val accFilename = "accPitchPhoneSensor"
    private val combFilename = "combPitchPhoneSensor"

    // filter
    private var xAccFilter = AccFilter()
    private var yAccFilter = AccFilter()
    private var zAccFilter = AccFilter()
    private var yGyroFilter = AccFilter()
    private var gyroFilter = CombFilter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_phonesensor)

        // creating sensor manager
        mSensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        // accelerometer sensor
        mAccSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        mGyroSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        // register sensor listener
        mSensorManager.registerListener(this, mAccSensor, SensorManager.SENSOR_DELAY_UI)
        mSensorManager.registerListener(this, mGyroSensor, SensorManager.SENSOR_DELAY_GAME)

        // assign TextView
        mAccOnlyTextView = findViewById(R.id.onlyAcctextView)
        mGyroAndAccTextView = findViewById(R.id.gyroAndAcctextView)

        // buttons
        mRecordButton = findViewById(R.id.record_Button)
        mRecordButton.setOnClickListener { record() }
        mTimeButton = findViewById(R.id.timeSelectButton)
    }

    override fun onResume() {
        super.onResume()
        mSensorManager.registerListener(
            this,
            mAccSensor,
            SensorManager.SENSOR_DELAY_UI
        )
        mSensorManager.registerListener(
            this,
            mGyroSensor,
            SensorManager.SENSOR_DELAY_GAME
        )
    }

    override fun onPause() {
        super.onPause()
        mSensorManager.unregisterListener(this)
        record = false
    }

    override fun onSensorChanged(sensorEvent: SensorEvent?) {
        if (sensorEvent != null) {
            if (sensorEvent.sensor.type == Sensor.TYPE_ACCELEROMETER) {

                accPitchEvent(sensorEvent)
                if (record) {
                    if (!onlyOnce) {
                        mStartTimestamp = timestamp
                        onlyOnce = true
                    }

                    openFileOutput(accFilename, Context.MODE_APPEND).use {
                        it.write("$mAccPitch\t\t\t\t${(sensorEvent.timestamp - mStartTimestamp) / 1000000.0}\n".toByteArray())
                    }
                }
            } else if (sensorEvent.sensor.type == Sensor.TYPE_GYROSCOPE) {
                combPitchEvent(sensorEvent)
                if (record) {
                    if (!onlyOnce2) {
                        mStartTimestamp2 = timestamp
                        onlyOnce2 = true
                    }

                    openFileOutput(combFilename, Context.MODE_APPEND).use {
                        it.write("$mCombPitch\t\t\t\t${(sensorEvent.timestamp - mStartTimestamp2) / 1000000.0}\n".toByteArray())
                    }
                }
            }
            runOnUiThread {
                updateTextViews()
            }
        }
    }

    private fun updateTextViews() {
        mAccOnlyTextView.text = String.format(getString(R.string.format), round(mAccPitch))
        mGyroAndAccTextView.text = String.format(getString(R.string.format), round(mCombPitch))
    }

    private fun accPitchEvent(values: SensorEvent) {
        mAccX = xAccFilter.filter(values.values[0])
        mAccY = yAccFilter.filter(values.values[1])
        mAccZ = zAccFilter.filter(values.values[2])

        val temp = mAccX / sqrt((mAccY * mAccY) + (mAccZ * mAccZ))

        mAccPitch = (90 - (atan(temp) * (180 / Math.PI))).toFloat()
    }

    private fun combPitchEvent(values: SensorEvent) {
        val elapsedTime = values.timestamp - timestamp
        val dT = elapsedTime / 1_000_000_000 // nanosecond to second
        timestamp = values.timestamp

        mGyroY = yGyroFilter.filter(values.values[1])

        mCombPitch = gyroFilter.filter(mGyroY, dT, mAccPitch)
    }


    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        // not used
    }

    private fun record() {
        if (!record) {
            val dir = filesDir
            val accFile = File(dir, accFilename)
            accFile.delete()
            val combFile = File(dir, combFilename)
            combFile.delete()
            record = true
            onlyOnce = false
            onlyOnce2 = false
            startTime = SystemClock.elapsedRealtimeNanos()
            mRecordButton.text = getString(R.string.Recording)

            Handler().postDelayed({
                record = false
                mRecordButton.text = getString(R.string.record)
            }, recordTime)
        } else {
            record = false
            mRecordButton.text = getString(R.string.record)
        }
    }

    fun backToSensorSelection(view: android.view.View) {
        startActivity(Intent(this, MainActivity::class.java))
    }

    fun selectTime(view: android.view.View) {
        if (recordTime == 10000L) {
            recordTime = 1000L
            mTimeButton.text = "1 second"
        } else if (recordTime == 1000L) {
            recordTime = 10000L
            mTimeButton.text = "10 seconds"
        }
    }
}