package com.example.lab3

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button

class MainActivity : AppCompatActivity() {

    private lateinit var mPhoneSensorButton: Button
    private lateinit var mMoveSensorButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mPhoneSensorButton = findViewById(R.id.phoneSensorButton)
        mMoveSensorButton = findViewById(R.id.moveSensorButton)

        mPhoneSensorButton.setOnClickListener {
            val intent = Intent(this, PhoneSensorActivity::class.java)
            startActivity(intent)
            finish()
        }

        mMoveSensorButton.setOnClickListener {
            val intent = Intent(this, ScanActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

}