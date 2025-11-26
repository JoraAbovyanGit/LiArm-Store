package com.example.plsworkver3.car

import android.content.Intent
import android.util.Log
import androidx.car.app.CarAppService
import androidx.car.app.validation.HostValidator
import androidx.car.app.Session
import androidx.car.app.Screen


class LiAutoCarAppService : CarAppService() {
    override fun onCreateSession(): Session = object : Session() {
        override fun onCreateScreen(intent: Intent): Screen {
            Log.d("LiAutoCarAppService", "onCreateScreen called")
            return LiAutoMainScreen(carContext)
        }
    }

    override fun createHostValidator(): HostValidator {
        Log.d("LiAutoCarAppService", "createHostValidator called - allowing all hosts")
        // Allow any host for now; tighten this when distributing publicly
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }
}

