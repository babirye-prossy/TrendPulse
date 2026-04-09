package com.trendpulse.trendpulse.device

import android.hardware.Sensor
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

data class SensorSelection(val type: Int, val name: String, val icon: ImageVector)

@Composable
fun SensorsScreen(
    viewModel: SensorsViewModel,
    onBackClick: () -> Unit
) {
    val sensorSelections = listOf(
        SensorSelection(Sensor.TYPE_ACCELEROMETER, "Accel", Icons.Default.DirectionsRun),
        SensorSelection(Sensor.TYPE_GYROSCOPE, "Gyro", Icons.Default.RotateRight),
        SensorSelection(Sensor.TYPE_PROXIMITY, "Prox", Icons.Default.SettingsInputAntenna),
        SensorSelection(Sensor.TYPE_LIGHT, "Light", Icons.Default.LightMode)
    )

    var selectedSensor by remember { mutableStateOf(sensorSelections[0]) }

    DisposableEffect(Unit) {
        viewModel.startListening()
        onDispose {
            viewModel.stopListening()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hardware Sensors") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                backgroundColor = MaterialTheme.colors.surface,
                elevation = 0.dp
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(sensorSelections) { selection ->
                    SensorChip(
                        selection = selection,
                        isSelected = selectedSensor.type == selection.type,
                        onClick = { selectedSensor = selection }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            SensorDisplay(
                selectedSensor = selectedSensor,
                viewModel = viewModel
            )
        }
    }
}

@Composable
fun SensorChip(
    selection: SensorSelection,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = if (isSelected) MaterialTheme.colors.primary else MaterialTheme.colors.surface,
        border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f)),
        elevation = if (isSelected) 4.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = selection.icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (isSelected) MaterialTheme.colors.onPrimary else MaterialTheme.colors.onSurface
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = selection.name,
                style = MaterialTheme.typography.body2,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) MaterialTheme.colors.onPrimary else MaterialTheme.colors.onSurface
            )
        }
    }
}

@Composable
fun SensorDisplay(
    selectedSensor: SensorSelection,
    viewModel: SensorsViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(80.dp),
            shape = CircleShape,
            color = MaterialTheme.colors.primary.copy(alpha = 0.1f)
        ) {
            Icon(
                imageVector = selectedSensor.icon,
                contentDescription = null,
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxSize(),
                tint = MaterialTheme.colors.primary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = selectedSensor.name,
            style = MaterialTheme.typography.h5,
            fontWeight = FontWeight.Bold
        )

        val dataText = when (selectedSensor.type) {
            Sensor.TYPE_ACCELEROMETER -> viewModel.accelerometerData.observeAsState("Waiting...").value
            Sensor.TYPE_GYROSCOPE -> viewModel.gyroscopeData.observeAsState("Waiting...").value
            Sensor.TYPE_PROXIMITY -> viewModel.proximityData.observeAsState("Waiting...").value
            Sensor.TYPE_LIGHT -> viewModel.lightData.observeAsState("Waiting...").value
            else -> "N/A"
        }

        Text(
            text = dataText,
            style = MaterialTheme.typography.body1,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Visualization
        when (selectedSensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val values by viewModel.accelerometerValues.observeAsState(floatArrayOf(0f, 0f, 0f))
                MotionVisualizer(values)
            }
            Sensor.TYPE_GYROSCOPE -> {
                val values by viewModel.gyroscopeValues.observeAsState(floatArrayOf(0f, 0f, 0f))
                MotionVisualizer(values)
            }
            Sensor.TYPE_PROXIMITY -> {
                val value by viewModel.proximityValue.observeAsState(10f)
                ProximityVisualizer(value)
            }
            Sensor.TYPE_LIGHT -> {
                val value by viewModel.lightValue.observeAsState(0f)
                LightVisualizer(value)
            }
        }
    }
}

@Composable
fun MotionVisualizer(values: FloatArray) {
    val labels = listOf("X", "Y", "Z")
    val colors = listOf(Color.Red, Color(0xFF4CAF50), Color.Blue)
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        values.forEachIndexed { index, value ->
            val animatedHeight by animateFloatAsState(targetValue = abs(value) * 15f)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "%.1f".format(value), style = MaterialTheme.typography.caption)
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(animatedHeight.coerceAtMost(180f).dp)
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .background(colors[index].copy(alpha = 0.7f))
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = labels[index], fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ProximityVisualizer(value: Float) {
    val isNear = value < 1.0f
    val animatedScale by animateFloatAsState(targetValue = if (isNear) 1.5f else 1.0f)
    
    Box(
        modifier = Modifier
            .size(150.dp)
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.size(60.dp * animatedScale),
            shape = CircleShape,
            color = if (isNear) Color.Red else Color(0xFF4CAF50),
            elevation = 8.dp
        ) {}
        Text(
            text = if (isNear) "NEAR" else "FAR",
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
fun LightVisualizer(value: Float) {
    val luxLimit = 1000f
    val percent = (value.coerceAtMost(luxLimit) / luxLimit)
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(150.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = percent,
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 12.dp,
                color = Color(0xFFFFC107)
            )
            Icon(
                imageVector = Icons.Default.WbSunny,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = Color(0xFFFFC107)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "${value.toInt()} LUX", style = MaterialTheme.typography.h6)
    }
}
