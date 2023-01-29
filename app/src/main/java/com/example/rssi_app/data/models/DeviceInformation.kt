package com.example.rssi_app.data

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class DeviceInformation(
    @SerializedName("imei")
    val imei: String,
    @SerializedName("wifiDetails")
    val wifiDetails: WifiDetails
): Parcelable

@Parcelize
data class WifiDetails(
    @SerializedName("rssi")
    val rssi: Int = -1,
    @SerializedName("ssid")
    val ssid: String = "unknown",
    @SerializedName("networkId")
    val networkId: Int = -1,
    @SerializedName("linkSpeed")
    val linkSpeed: Int = -1
) : Parcelable