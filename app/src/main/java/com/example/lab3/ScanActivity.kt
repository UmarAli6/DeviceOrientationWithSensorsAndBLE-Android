package com.example.lab3

import android.Manifest
import androidx.appcompat.app.AppCompatActivity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import com.example.lab3.uiutils.BtDeviceAdapter
import android.widget.TextView
import android.os.Bundle
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import android.content.pm.PackageManager
import com.example.lab3.uiutils.MsgUtils
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import android.content.Intent
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.os.Handler
import android.util.Log
import android.widget.Button
import java.util.ArrayList


/**
 *  Code was provided by teacher Anders Lindström.
 */
class ScanActivity : AppCompatActivity() {
    private var mBluetoothAdapter: BluetoothAdapter? = null
    private var mScanning = false
    private var mHandler: Handler? = null
    private var mDeviceList: ArrayList<BluetoothDevice>? = null
    private var mBtDeviceAdapter: BtDeviceAdapter? = null
    private var mScanInfoView: TextView? = null

    /**
     * Below: Manage bluetooth initialization and life cycle
     * via Activity.onCreate, onStart and onStop.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)
        mDeviceList = ArrayList()
        mHandler = Handler()

        // ui stuff
        mScanInfoView = findViewById(R.id.scan_info)
        val startScanButton = findViewById<Button>(R.id.start_scan_button)
        startScanButton.setOnClickListener {
            mDeviceList!!.clear()
            scanForDevices(true)
        }

        // more ui stuff, the recycler view
        val recyclerView = findViewById<RecyclerView>(R.id.scan_list_view)
        val layoutManager = LinearLayoutManager(this)
        recyclerView.layoutManager = layoutManager
        mBtDeviceAdapter = BtDeviceAdapter(
            mDeviceList
        ) { position -> onDeviceSelected(position) }
        recyclerView.adapter = mBtDeviceAdapter
    }

    override fun onStart() {
        super.onStart()
        mScanInfoView!!.setText(R.string.no_devices_found)
        initBLE()
    }

    override fun onStop() {
        super.onStop()
        // stop scanning
        scanForDevices(false)
        mDeviceList!!.clear()
        mBtDeviceAdapter!!.notifyDataSetChanged()
    }

    // Check BLE permissions and turn on BT (if turned off) - user interaction(s)
    private fun initBLE() {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            MsgUtils.showToast("BLE is not supported", this)
            finish()
        } else {
            MsgUtils.showToast("BLE is supported", this)
            // Access Location is a "dangerous" permission
            val hasAccessLocation = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            if (hasAccessLocation != PackageManager.PERMISSION_GRANTED) {
                // ask the user for permission
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    REQUEST_ACCESS_LOCATION
                )
                // the callback method onRequestPermissionsResult gets the result of this request
            }
        }
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        // turn on BT
        if (mBluetoothAdapter == null || !mBluetoothAdapter!!.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        }
    }

    /*
     * Device selected, start DeviceActivity (displaying data)
     */
    private fun onDeviceSelected(position: Int) {
        val selectedDevice = mDeviceList!![position]
        // BluetoothDevice objects are parceable, i.e. we can "send" the selected device
        // to the DeviceActivity packaged in an intent.
        val intent = Intent(this@ScanActivity, MovesenseActivity::class.java)
        intent.putExtra(SELECTED_DEVICE, selectedDevice)
        startActivity(intent)
    }

    /*
     * Scan for BLE devices.
     */
    private fun scanForDevices(enable: Boolean) {
        val scanner = mBluetoothAdapter!!.bluetoothLeScanner
        if (enable) {
            if (!mScanning) {
                // stop scanning after a pre-defined scan period, SCAN_PERIOD
                mHandler!!.postDelayed({
                    if (mScanning) {
                        mScanning = false
                        scanner.stopScan(mScanCallback)
                        MsgUtils.showToast("BLE scan stopped", this@ScanActivity)
                    }
                }, SCAN_PERIOD)
                mScanning = true
                // TODO: Add a filter, e.g. for heart rate service, scan settings
                scanner.startScan(mScanCallback)
                mScanInfoView!!.setText(R.string.no_devices_found)
                MsgUtils.showToast("BLE scan started", this)
            }
        } else {
            if (mScanning) {
                mScanning = false
                scanner.stopScan(mScanCallback)
                MsgUtils.showToast("BLE scan stopped", this)
            }
        }
    }

    /*
     * Implementation of scan callback methods
     */
    private val mScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            //Log.i(LOG_TAG, "onScanResult");
            val device = result.device
            val name = device.name
            mHandler!!.post {
                if (name != null && name.contains(MOVESENSE)
                    && !mDeviceList!!.contains(device)
                ) {
                    mDeviceList!!.add(device)
                    mBtDeviceAdapter!!.notifyDataSetChanged()
                    val info = """Found ${mDeviceList!!.size} device(s)
Touch to connect"""
                    mScanInfoView!!.text = info
                    Log.i(LOG_TAG, device.toString())
                }
            }
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            super.onBatchScanResults(results)
            Log.i(LOG_TAG, "onBatchScanResult")
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.i(LOG_TAG, "onScanFailed")
        }
    }

    // callback for Activity.requestPermissions
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_ACCESS_LOCATION) {
            // if request is cancelled, the results array is empty
            if (grantResults.size == 0
                || grantResults[0] != PackageManager.PERMISSION_GRANTED
            ) {
                // stop this activity
                finish()
            }
        }
    }

    // callback for request to turn on BT
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        // if user chooses not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == RESULT_CANCELED) {
            finish()
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    fun backToSensorSelection(view: android.view.View) {
        startActivity(Intent(this, MainActivity::class.java))
    }

    companion object {
        const val MOVESENSE = "Movesense"
        const val REQUEST_ENABLE_BT = 1000
        const val REQUEST_ACCESS_LOCATION = 1001
        var SELECTED_DEVICE = "Selected device"
        private const val SCAN_PERIOD: Long = 5000 // milliseconds
        private const val LOG_TAG = "ScanActivity"
    }
}