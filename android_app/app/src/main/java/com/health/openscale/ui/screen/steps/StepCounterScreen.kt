package com.health.openscale.ui.screen.steps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@Composable
fun StepCounterScreen() {
    val context = LocalContext.current

    val sensorManager = remember {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    val stepSensor = remember {
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    }

    var stepCount by remember { mutableStateOf(0) }
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
    }

    DisposableEffect(permissionGranted, stepSensor) {
        var listener: SensorEventListener? = null

        if (permissionGranted && stepSensor != null) {
            listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    val value = event?.values?.firstOrNull()?.toInt()
                    if (value != null) stepCount = value
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }

            sensorManager.registerListener(
                listener,
                stepSensor,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }

        onDispose {
            listener?.let { sensorManager.unregisterListener(it) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("شمارش قدم", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        when {
            !permissionGranted -> {
                Text("برای خواندن سنسور قدم، اجازه‌ی فعالیت بدنی لازم است.")
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                    }
                ) {
                    Text("دادن اجازه")
                }
            }

            stepSensor == null -> {
                Text("سنسور شمارش قدم در این گوشی پیدا نشد.")
            }

            else -> {
                Text(
                    text = stepCount.toString(),
                    style = MaterialTheme.typography.displayLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("شمارنده‌ی حسگر از آخرین روشن‌شدن گوشی است، نه قدم‌های امروز.")
            }
        }
    }
}
