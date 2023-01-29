package com.example.rssi_app.service

import android.annotation.SuppressLint
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class WifiDetailsWorker@AssistedInject constructor(@Assisted val context: Context,  @dagger.assisted.Assisted params: WorkerParameters) : CoroutineWorker(context, params) {
    private val rssi = WifiWorkerHelper(context)

    @SuppressLint("HardwareIds", "WifiManagerPotentialLeak")
    override suspend fun doWork(): Result {
        rssi.getWifiInfo()
        return Result.success()
    }
}