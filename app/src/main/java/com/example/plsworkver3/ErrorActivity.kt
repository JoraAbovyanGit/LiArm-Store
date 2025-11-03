package com.example.plsworkver3

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class ErrorActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_error)
    }

    fun onRetryClick(@Suppress("UNUSED_PARAMETER") view: android.view.View) {
        startActivity(android.content.Intent(this, MainActivity::class.java))
        finish()
    }
}


