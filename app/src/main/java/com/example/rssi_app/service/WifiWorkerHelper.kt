package com.example.rssi_app.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.example.rssi_app.R
import com.example.rssi_app.data.DeviceInformation
import com.example.rssi_app.data.WifiDetails
import com.example.rssi_app.ui.MainActivity
import kotlinx.coroutines.flow.*

class WifiWorkerHelper(val context: Context) {
    var wifiDetails: Flow<Result<DeviceInformation>> = object : Flow<Result<DeviceInformation>> {
        override suspend fun collect(collector: FlowCollector<Result<DeviceInformation>>) {}
    }

    fun getWifiInfo() {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        val networkCallback = object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
            @SuppressLint("HardwareIds")
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                super.onCapabilitiesChanged(network, networkCapabilities)
                val wifiInfo = networkCapabilities.transportInfo as WifiInfo

                wifiDetails = flow {
                    val wifiDetails = WifiDetails(wifiInfo.rssi, wifiInfo.ssid, wifiInfo.networkId, wifiInfo.linkSpeed)
                    val deviceInfoRequest = DeviceInformation(Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID), wifiDetails)
                    emit(Result.success(deviceInfoRequest))
                    showNotification(context, deviceInfoRequest)
                }.catch {
                    emit(Result.failure(RuntimeException("Error occured")))
                }
            }
        }

        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager.registerNetworkCallback(request, networkCallback)
    }

    private fun showNotification(
        context: Context,
        deviceInfo: DeviceInformation?,
        error: Boolean = false
    ) {
        val id = context.getString(R.string.notification_channel_id)
        val title = context.getString(R.string.notification_title)
        val cancel = context.getString(R.string.cancel)

        val notificationIntent = Intent(context, MainActivity::class.java)
        notificationIntent.putExtra(EXTRA_KEY, "todo")
        notificationIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        val pendingIntent = PendingIntent.getActivity(context, NOTIFICATION_ID, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(context, id)
            .setContentTitle(title)
            .setTicker(title)
            .setContentText(if (error) context.getString(R.string.error) else "${deviceInfo?.wifiDetails} available wifi information stored")
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setOngoing(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val CODE_LOCATION_PERMISSION = 0
        const val CHANNEL_ID = "sendingData"
        const val EXTRA_KEY = "deviceInfo"
        const val NOTIFICATION_ID = 20231
    }
}