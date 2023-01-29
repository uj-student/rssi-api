package com.example.rssi_app.service

import android.content.Context
import com.example.rssi_app.data.DeviceInformation
import com.example.rssi_app.data.WifiDetails
import com.example.rssi_app.data.WifiDetailsService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import javax.inject.Inject

class WifiDetailsRepo @Inject constructor(
    private val service: WifiDetailsService,
    @ApplicationContext private val context: Context
) {
    private val helper: WifiWorkerHelper = WifiWorkerHelper(context)
    private val defaultValue = DeviceInformation("", WifiDetails())

    suspend fun sendWifiDetails(): Flow<Result<DeviceInformation>> {
        helper.getWifiInfo()
        helper.wifiDetails.collectLatest {
            if (it.isSuccess)
                service.uploadWifiInfo(it.getOrDefault(defaultValue))
        }

        val request = helper.wifiDetails.map {
            Result.success(it.getOrNull()!!).getOrNull()!!
        }.firstOrNull() ?: defaultValue

        return service.uploadWifiInfo(request)
    }
}
