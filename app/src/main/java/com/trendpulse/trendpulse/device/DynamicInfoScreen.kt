package com.trendpulse.trendpulse.device

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DynamicInfoScreen(
    viewModel: DeviceViewModel,
    onBackClick: () -> Unit
) {
    val batteryStatus by viewModel.batteryStatus.observeAsState("")
    val memoryStatus by viewModel.memoryStatus.observeAsState("")
    val networkDetails by viewModel.networkDetails.observeAsState(emptyMap())
    val cpuActivity by viewModel.cpuActivity.observeAsState(emptyList())
    val broadcastLogs by viewModel.broadcastLogs.observeAsState(emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("System Monitoring") },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SectionTitle("Battery & Memory")
                Row(modifier = Modifier.fillMaxWidth()) {
                    val batteryLevel = batteryStatus.filter { it.isDigit() }.toIntOrNull() ?: 0
                    StatusCard(
                        modifier = Modifier.weight(1f),
                        title = "Battery",
                        value = "$batteryLevel%",
                        progress = batteryLevel / 100f,
                        detail = batteryStatus,
                        color = if (batteryLevel > 20) Color(0xFF4CAF50) else Color.Red
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    val memParts = memoryStatus.replace("RAM: ", "").split(" / ")
                    var memPercent = 0f
                    var memDetail = memoryStatus
                    if (memParts.size == 2) {
                        val avail = memParts[0].replace("MB", "").toLongOrNull() ?: 0
                        val total = memParts[1].replace("MB", "").toLongOrNull() ?: 1
                        val used = total - avail
                        memPercent = used.toFloat() / total.toFloat()
                        memDetail = "Used: ${used}MB / Total: ${total}MB"
                    }
                    
                    StatusCard(
                        modifier = Modifier.weight(1f),
                        title = "Memory",
                        value = "${(memPercent * 100).toInt()}%",
                        progress = memPercent,
                        detail = memDetail,
                        color = MaterialTheme.colors.primary
                    )
                }
            }

            item {
                SectionTitle("Network Details")
                Card(
                    shape = RoundedCornerShape(16.dp),
                    elevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        NetworkRow("Type", networkDetails["Type"] ?: "Unknown")
                        NetworkRow("Status", networkDetails["Status"] ?: "Unknown", 
                            valueColor = if (networkDetails["Status"] == "Online") Color(0xFF4CAF50) else Color.Red)
                        NetworkRow("IP Address", networkDetails["IP"] ?: "Unavailable")
                        NetworkRow("SSID", networkDetails["SSID"] ?: "N/A")
                    }
                }
            }

            item {
                SectionTitle("CPU Activity")
                Card(
                    shape = RoundedCornerShape(16.dp),
                    elevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            cpuActivity.forEach { activity ->
                                val animatedHeight by animateFloatAsState(targetValue = activity.toFloat())
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(animatedHeight / 100f)
                                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                        .background(MaterialTheme.colors.primary.copy(alpha = 0.7f))
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Active cores: ${cpuActivity.size}",
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            item {
                SectionTitle("Broadcast Logs")
            }
            
            items(broadcastLogs.take(10)) { log ->
                LogItem(log)
            }
            
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.subtitle1,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun StatusCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    progress: Float,
    detail: String,
    color: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        elevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.caption)
            Text(value, style = MaterialTheme.typography.h5, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = color,
                backgroundColor = color.copy(alpha = 0.2f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(detail, fontSize = 10.sp, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
        }
    }
}

@Composable
fun NetworkRow(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.body2, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
        Text(value, style = MaterialTheme.typography.body2, fontWeight = FontWeight.Medium, color = valueColor)
    }
}

@Composable
fun LogItem(log: String) {
    Card(
        shape = RoundedCornerShape(8.dp),
        backgroundColor = MaterialTheme.colors.onSurface.copy(alpha = 0.05f),
        elevation = 0.dp,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    ) {
        Text(
            text = log,
            modifier = Modifier.padding(8.dp),
            style = MaterialTheme.typography.caption
        )
    }
}
