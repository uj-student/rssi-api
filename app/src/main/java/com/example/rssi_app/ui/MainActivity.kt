package com.example.rssi_app.ui

import android.Manifest
import android.content.Context
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color.Companion.White
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.work.*
import com.example.rssi_app.R
import com.example.rssi_app.data.DeviceInformation
import com.example.rssi_app.data.FakeAPi
import com.example.rssi_app.data.WifiDetails
import com.example.rssi_app.data.WifiDetailsService
import com.example.rssi_app.service.WifiDetailsRepo
import com.example.rssi_app.service.WifiDetailsWorker
import com.example.rssi_app.service.WifiWorkerHelper.Companion.CODE_LOCATION_PERMISSION
import com.example.rssi_app.ui.theme.RssiappTheme
import com.example.rssi_app.vireModel.WifiViewModel
import com.google.gson.GsonBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import pub.devrel.easypermissions.AppSettingsDialog
import pub.devrel.easypermissions.EasyPermissions
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class MainActivity : ComponentActivity(), EasyPermissions.PermissionCallbacks {
    private val viewModel: WifiViewModel by viewModels()

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        if (it) {

        } else {

        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        workManager(applicationContext)
        requestPermission()
        if (hasPermission()) viewModel.uploadWifiInfo()

        setContent {
            RssiappTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
                    HomeScreen(viewModel, { hasPermission() }) { requestPermission() }
                }
            }
        }
    }

    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) {}

    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>) {
        if (EasyPermissions.somePermissionPermanentlyDenied(this, perms)) {
            AppSettingsDialog.Builder(this).setThemeResId(R.style.AlertDialogTheme).build().show()
        } else {
            requestPermission()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    private fun workManager(context: Context) {
        val workerRequest = PeriodicWorkRequestBuilder<WifiDetailsWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInitialDelay(1, TimeUnit.MINUTES)
            .addTag("rssiWorker")
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "rssiWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            workerRequest
        )
    }

    private fun requestPermission() {
        if (hasPermission()) {
            return
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                EasyPermissions.requestPermissions(
                    this,
                    "Please grant all requested permissions and chose allow all the time for the app to send background location",
                    CODE_LOCATION_PERMISSION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                    Manifest.permission.POST_NOTIFICATIONS
                )
            } else {
                EasyPermissions.requestPermissions(
                    this,
                    "Please grant location permission and chose allow all the time for the app to send background location",
                    CODE_LOCATION_PERMISSION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                )
            }
        }
    }

    private fun hasPermission(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        EasyPermissions.hasPermissions(
            applicationContext,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS
        ) && isLocationEnabled()
    } else {
        EasyPermissions.hasPermissions(
            applicationContext,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) && isLocationEnabled()
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager: LocationManager = applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
}

@Composable
fun HomeScreen(viewModel: WifiViewModel, hasPermission: () -> Boolean, requestPermission: () -> Unit) {
    val showProgress = remember { mutableStateOf(false) }
    val prettyJson = remember { mutableStateOf("") }
    val isResponseEmpty = remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        if (prettyJson.value.isNotEmpty()) {
            isResponseEmpty.value = false
            Text(text = prettyJson.value)
        }
        Button(
            modifier = Modifier
                .wrapContentHeight()
                .wrapContentWidth(),
            onClick = {
                if (!hasPermission.invoke()) {
                    requestPermission.invoke()
                } else {
                    viewModel.uploadWifiInfo()
                }
            }) {
            Text(text = if (prettyJson.value.isEmpty()) "Run Scan" else "Run Scan Again")
        }
    }

    if (showProgress.value) {
        ProgressDialog()
    }

    LaunchedEffect(key1 = "homeScreen") {
        coroutineScope.launch {
            viewModel.wifiDeviceState.collect {
                when (it) {
                    is WifiViewModel.ViewAction.Loading -> showProgress.value = true
                    is WifiViewModel.ViewAction.DeviceResponse -> {
                        showProgress.value = false
                        val gson = GsonBuilder().setPrettyPrinting().create()
                        prettyJson.value = gson.toJson(it.deviceInfo)
                    }
                    WifiViewModel.ViewAction.Empty -> Unit
                }
            }
        }
    }
}

@Composable
fun ProgressDialog() {
    val dialogState = remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { dialogState.value = false },
        DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(120.dp)
                .background(White, shape = RoundedCornerShape(12.dp))
        ) {
            Column {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                Text(text = "Loading...", Modifier.padding(4.dp))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    RssiappTheme {
        HomeScreen(
            WifiViewModel(
                WifiDetailsRepo(
                    WifiDetailsService(object : FakeAPi {
                        override suspend fun sendWifiDetails(deviceInfo: DeviceInformation): DeviceInformation = DeviceInformation(
                            "",
                            WifiDetails(
                                0,
                                "",
                                0,
                                0
                            )
                        )
                    }),
                    LocalContext.current
                ),
            ), { true }) {}
    }
}