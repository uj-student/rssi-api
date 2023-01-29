package com.example.rssi_app.vireModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rssi_app.data.DeviceInformation
import com.example.rssi_app.service.WifiDetailsRepo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WifiViewModel @Inject constructor(private val repository: WifiDetailsRepo) : ViewModel() {

    private val wifiState = MutableStateFlow<ViewAction>(ViewAction.Empty)
    val wifiDeviceState: MutableStateFlow<ViewAction> = wifiState

    fun uploadWifiInfo() {
        wifiState.value = ViewAction.Loading(true)
        viewModelScope.launch {
            repository.sendWifiDetails()
                .onEach {
                    wifiState.value = ViewAction.Loading(false)
                }.collect {
                    wifiState.value = ViewAction.DeviceResponse(it)
                }
        }
    }

    sealed class ViewAction {
        data class Loading(val showProgress: Boolean) : ViewAction()
        data class DeviceResponse(val deviceInfo: Result<DeviceInformation>) : ViewAction()
        object Empty : ViewAction()
    }
}