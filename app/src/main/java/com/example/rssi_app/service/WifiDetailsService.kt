package com.example.rssi_app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import retrofit2.http.Body
import retrofit2.http.POST
import javax.inject.Inject

interface FakeAPi {
    @POST("/posts")
    suspend fun sendWifiDetails(@Body deviceInfo: DeviceInformation): DeviceInformation
}

class WifiDetailsService @Inject constructor(private val fakeApi: FakeAPi) {
    suspend fun uploadWifiInfo(deviceInfo: DeviceInformation): Flow<Result<DeviceInformation>> {
        return flow {
            emit(Result.success(fakeApi.sendWifiDetails(deviceInfo)))
        }.catch {
            emit(Result.failure(RuntimeException("Couldn't process")))
        }
    }
}
