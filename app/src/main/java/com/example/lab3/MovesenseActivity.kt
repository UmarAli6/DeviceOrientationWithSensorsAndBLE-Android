package com.example.lab3

import androidx.appcompat.app.AppCompatActivity
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.widget.TextView
import android.os.Bundle
import com.example.lab3.uiutils.MsgUtils
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.SystemClock
import android.util.Log
import android.widget.Button
import com.example.lab3.filter.CombFilter
import com.example.lab3.filter.AccFilter
import com.example.lab3.utils.TypeConverter
import java.io.File
import java.lang.Exception
import java.util.*
import kotlin.math.atan
import kotlin.math.round
import kotlin.math.sqrt


/**
 * Majority of the code was provided by teacher Anders Lindström.
 * Some changes were made for the assignment (Mainly in onCharacteristicChanged)
 */
class MovesenseActivity : AppCompatActivity() {

    // Movesense
    private val IMU_COMMAND = "/Meas/IMU6/13" // see documentation
    private val MOVESENSE_RESPONSE: Byte = 2
    private val REQUEST_ID: Byte = 99

    // Bluetooth
    private var mSelectedDevice: BluetoothDevice? = null
    private var mBluetoothGatt: BluetoothGatt? = null

    // Handler
    private var mHandler: Handler? = null

    // UI
    private lateinit var mDeviceNameTextView: TextView
    private lateinit var mInfoTextView: TextView
    private lateinit var mAccPitchTextView: TextView
    private lateinit var mGyroPitchTextView: TextView
    private lateinit var mRecordButton: Button
    private lateinit var mTimeButton: Button

    // flags
    private var record = false
    private var onlyOnce = false
    private var onlyOnce2 = false

    // filter
    private var xAccFilter = AccFilter()
    private var yAccFilter = AccFilter()
    private var zAccFilter = AccFilter()
    private var yGyroFilter = AccFilter()
    private var gyroFilter = CombFilter()

    // Other stuff for filter
    private var mAccX = 0.0f
    private var mAccY = 0.0f
    private var mAccZ = 0.0f
    private var mAccPitch = 0.0f
    private var mCombPitch = 0.0f
    private var mGyroY = 0.0f

    // time
    private var mTimestamp = 0L
    private var startTime = 0L
    private var mStartTimestamp = 0L
    private var mStartTimestamp2 = 0L
    private var recordTime = 10000L


    // Filenames
    private val accFilename = "accPitchMovesenseSensor"
    private val combFilename = "combPitchMovesenseSensor"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_movesense)

        // assign TextView
        mDeviceNameTextView = findViewById(R.id.device_name_TextView)
        mInfoTextView = findViewById(R.id.info_TextView)
        mAccPitchTextView = findViewById(R.id.accPitch_TextView)
        mGyroPitchTextView = findViewById(R.id.combPitch_TextView)

        // buttons
        mRecordButton = findViewById(R.id.record_Button)
        mRecordButton.setOnClickListener { record() }
        mTimeButton = findViewById(R.id.timeSelect_Button)

        val intent = intent
        // Get the selected device from the intent
        mSelectedDevice = intent.getParcelableExtra(ScanActivity.SELECTED_DEVICE)
        if (mSelectedDevice == null) {
            MsgUtils.createDialog("Error", "No device found", this).show()
            mDeviceNameTextView.setText(R.string.no_device)
        } else {
            mDeviceNameTextView.text = mSelectedDevice!!.name
        }

        // handler
        mHandler = Handler()
    }

    override fun onStart() {
        super.onStart()
        if (mSelectedDevice != null) {
            // Connect and register call backs for bluetooth gatt
            mBluetoothGatt = mSelectedDevice!!.connectGatt(this, false, mBtGattCallback)
        }
    }

    override fun onStop() {
        super.onStop()
        if (mBluetoothGatt != null) {
            mBluetoothGatt!!.disconnect()
            try {
                mBluetoothGatt!!.close()
            } catch (e: Exception) {
                // ugly, but this is to handle a bug in some versions in the Android BLE API
            }
        }
    }

    override fun onPause() {
        super.onPause()
        record = false
    }

    /**
     * Callbacks for bluetooth gatt changes/updates
     * The documentation is not always clear, but most callback methods seems to
     * be executed on a worker thread - hence use a Handler when updating the ui.
     */
    private val mBtGattCallback: BluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                mBluetoothGatt = gatt
                mHandler!!.post {
                    mAccPitchTextView.setText(R.string.connected)
                    mGyroPitchTextView.setText(R.string.connected)
                }
                // Discover services
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // Close connection and display info in ui
                mBluetoothGatt = null
                mHandler!!.post {
                    mAccPitchTextView.setText(R.string.disconnected)
                    mGyroPitchTextView.setText(R.string.disconnected)
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Debug: list discovered services
                val services = gatt.services
                for (service in services) {
                    Log.i(LOG_TAG, service.uuid.toString())
                }

                // Get the Movesense 2.0 IMU service
                val movesenseService = gatt.getService(MOVESENSE_2_0_SERVICE)
                if (movesenseService != null) {
                    // debug: service present, list characteristics
                    val characteristics = movesenseService.characteristics
                    for (chara in characteristics) {
                        Log.i(LOG_TAG, chara.uuid.toString())
                    }

                    // Write a command, as a byte array, to the command characteristic
                    // Callback: onCharacteristicWrite
                    val commandChar = movesenseService.getCharacteristic(
                        MOVESENSE_2_0_COMMAND_CHARACTERISTIC
                    )
                    val command = TypeConverter.stringToAsciiArray(REQUEST_ID, IMU_COMMAND)
                    commandChar.value = command
                    val wasSuccess = mBluetoothGatt!!.writeCharacteristic(commandChar)
                    Log.i("writeCharacteristic", "was success=$wasSuccess")
                } else {
                    mHandler!!.post {
                        MsgUtils.createDialog(
                            "Alert!",
                            getString(R.string.service_not_found),
                            this@MovesenseActivity
                        )
                            .show()
                    }
                }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            Log.i(LOG_TAG, "onCharacteristicWrite " + characteristic.uuid.toString())

            // Enable notifications on data from the sensor. First: Enable receiving
            // notifications on the client side, i.e. on this Android device.
            val movesenseService = gatt.getService(MOVESENSE_2_0_SERVICE)
            val dataCharacteristic = movesenseService.getCharacteristic(
                MOVESENSE_2_0_DATA_CHARACTERISTIC
            )
            // second arg: true, notification; false, indication
            val success = gatt.setCharacteristicNotification(dataCharacteristic, true)
            if (success) {
                Log.i(LOG_TAG, "setCharactNotification success")
                // Second: set enable notification server side (sensor). Why isn't
                // this done by setCharacteristicNotification - a flaw in the API?
                val descriptor = dataCharacteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor) // callback: onDescriptorWrite
            } else {
                Log.i(LOG_TAG, "setCharacteristicNotification failed")
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            Log.i(LOG_TAG, "onDescriptorWrite, status $status")
            if (CLIENT_CHARACTERISTIC_CONFIG == descriptor.uuid) if (status == BluetoothGatt.GATT_SUCCESS) {
                // if success, we should receive data in onCharacteristicChanged
                mHandler!!.post {
                    mInfoTextView.setText(R.string.notifications_enabled)
                }
            }
        }

        /**
         * Callback called on characteristic changes, e.g. when a sensor data value is changed.
         * This is where we receive notifications on new sensor data.
         */
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {

            if (MOVESENSE_2_0_DATA_CHARACTERISTIC == characteristic.uuid) {
                val data = characteristic.value
                if (data[0] == MOVESENSE_RESPONSE && data[1] == REQUEST_ID) {
                    val len = data.size
                    val timestamp = TypeConverter.fourBytesToInt(data, 2).toLong()

                    accPitchEvent(data)

                    if (record) {
                        if (!onlyOnce) {
                            mStartTimestamp = timestamp
                            onlyOnce = true
                        }

                        openFileOutput(accFilename, Context.MODE_APPEND).use {
                            it.write("$mAccPitch\t\t\t\t${(timestamp - mStartTimestamp) / 1.0}\n".toByteArray())
                        }
                    }

                    if (len > 18) {

                        combPitchEvent(data, timestamp)

                        if (record) {
                            if (!onlyOnce2) {
                                mStartTimestamp2 = timestamp
                                onlyOnce2 = true
                            }

                            openFileOutput(combFilename, Context.MODE_APPEND).use {
                                it.write("$mCombPitch\t\t\t\t${(timestamp - mStartTimestamp2) / 1.0}\n".toByteArray())
                            }
                        }
                    }
                    mHandler!!.post {
                        updateTextViews()
                    }
                }
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            Log.i(LOG_TAG, "onCharacteristicRead " + characteristic.uuid.toString())
        }
    }

    private fun accPitchEvent(data: ByteArray) {

        mAccX = xAccFilter.filter(TypeConverter.fourBytesToFloat(data, 6))
        mAccY = yAccFilter.filter(TypeConverter.fourBytesToFloat(data, 10))
        mAccZ = zAccFilter.filter(TypeConverter.fourBytesToFloat(data, 14))

        val temp = mAccX / sqrt((mAccY * mAccY) + (mAccZ * mAccZ))

        mAccPitch = (90 - (atan(temp) * (180 / Math.PI))).toFloat()
    }

    private fun combPitchEvent(data: ByteArray, timestamp: Long) {
        val elapsedTime = timestamp - mTimestamp
        val dT = elapsedTime / 1_000 // milisecond to second
        mTimestamp = timestamp

        mGyroY = TypeConverter.fourBytesToFloat(data, 22)
        mGyroY = yGyroFilter.filter(mGyroY)

        mCombPitch = gyroFilter.filter(mGyroY, dT, mAccPitch)
    }

    private fun updateTextViews() {
        mAccPitchTextView.text = String.format(getString(R.string.format), round(mAccPitch))
        mGyroPitchTextView.text = String.format(getString(R.string.format), round(mCombPitch))
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
            mTimeButton.text = getString(R.string.oneSecond)
        } else if (recordTime == 1000L) {
            recordTime = 10000L
            mTimeButton.text = getString(R.string.tenSeconds)
        }
    }

    companion object {
        val MOVESENSE_2_0_SERVICE = UUID.fromString("34802252-7185-4d5d-b431-630e7050e8f0")
        val MOVESENSE_2_0_COMMAND_CHARACTERISTIC =
            UUID.fromString("34800001-7185-4d5d-b431-630e7050e8f0")
        val MOVESENSE_2_0_DATA_CHARACTERISTIC =
            UUID.fromString("34800002-7185-4d5d-b431-630e7050e8f0")

        // UUID for the client characteristic, which is necessary for notifications
        val CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val LOG_TAG = "DeviceActivity"
    }
}
