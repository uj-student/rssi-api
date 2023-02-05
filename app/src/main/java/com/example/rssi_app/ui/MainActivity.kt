package com.example.rssi_app.ui

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.example.rssi_app.R
import com.example.rssi_app.data.DeviceInformation
import com.example.rssi_app.data.FakeAPi
import com.example.rssi_app.data.WifiDetails
import com.example.rssi_app.data.WifiDetailsService
import com.example.rssi_app.service.WifiDetailsRepo
import com.example.rssi_app.service.WifiDetailsWorker
import com.example.rssi_app.service.WifiWorkerHelper
import com.example.rssi_app.ui.theme.RssiappTheme
import com.example.rssi_app.vireModel.WifiViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.gson.GsonBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import pub.devrel.easypermissions.AppSettingsDialog
import pub.devrel.easypermissions.EasyPermissions
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class MainActivity : ComponentActivity() { //}, EasyPermissions.PermissionCallbacks {
    private val viewModel: WifiViewModel by viewModels()

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

//        if (hasPermission()) viewModel.uploadWifiInfo()

        setContent {
            RssiappTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
                    var hasNotificationPermission by remember {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            mutableStateOf(ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
                        } else {
                            mutableStateOf(false)
                        }
                    }

                    val permissionLauncher = rememberMultiplePermissionsState(
                        requiredPermissions().toList()
                    ) { permissionResult ->
                        hasNotificationPermission = permissionResult.any { it.value }
                        if (!hasNotificationPermission) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
                                    || shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
                                    || shouldShowRequestPermissionRationale(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                                ) {
                                    AppSettingsDialog.Builder(this).setThemeResId(androidx.appcompat.R.style.AlertDialog_AppCompat).build().show()
                                }
                            }
                        }
                    }

                    if (hasNotificationPermission && isLocationEnabled()) {
                        workManager(applicationContext)
                        HomeScreen(viewModel, hasNotificationPermission)
                    } else {
                        Button(
                            modifier = Modifier
                                .wrapContentHeight()
                                .wrapContentWidth(),
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    permissionLauncher.launchMultiplePermissionRequest()
                                }
                            }) {
                            Text("Grant Permissions")

                        }
                    }
                }
            }
        }
    }

    private fun workManager(context: Context) {
        val workerRequest = PeriodicWorkRequestBuilder<WifiDetailsWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag("rssiWorker")
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "rssiWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            workerRequest
        )
    }

    private fun requiredPermissions(): Array<String> {
        val requiredPermissions = arrayListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return requiredPermissions.toTypedArray()
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager: LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
}

@Composable
fun HomeScreen(viewModel: WifiViewModel, hasPermission: Boolean) {
    val showProgress = remember { mutableStateOf(false) }
    val prettyJson = remember { mutableStateOf("") }
    val isResponseEmpty = remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

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
                if (hasPermission) {
                    viewModel.uploadWifiInfo()
                } else {
                    Toast.makeText(context, "Please enable permissions and restart app", Toast.LENGTH_LONG).show()
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
            ), true
        )
    }
}