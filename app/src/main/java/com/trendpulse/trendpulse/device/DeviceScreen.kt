package com.trendpulse.trendpulse.device

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DeviceScreen(
    onDynamicInfoClick: () -> Unit,
    onSensorsClick: () -> Unit,
    onNotifyClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Device Dashboard",
                    style = MaterialTheme.typography.h4,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Monitor your hardware and system state",
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                )
            }
            IconButton(
                onClick = onNotifyClick,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "Notify",
                    tint = MaterialTheme.colors.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action Cards
        Row(modifier = Modifier.fillMaxWidth()) {
            DeviceActionCard(
                modifier = Modifier.weight(1f),
                title = "Real-time",
                subtitle = "RAM / Battery",
                icon = Icons.Default.Info,
                onClick = onDynamicInfoClick
            )
            Spacer(modifier = Modifier.width(16.dp))
            DeviceActionCard(
                modifier = Modifier.weight(1f),
                title = "Sensors",
                subtitle = "Live Data",
                icon = Icons.Default.Build,
                onClick = onSensorsClick
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Hardware Details",
            style = MaterialTheme.typography.h6,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        InfoSection(
            title = "Identity",
            items = listOf(
                "Manufacturer" to Build.MANUFACTURER,
                "Model" to Build.MODEL,
                "Brand" to Build.BRAND
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        InfoSection(
            title = "System",
            items = listOf(
                "Android" to Build.VERSION.RELEASE,
                "API Level" to Build.VERSION.SDK_INT.toString(),
                "Build ID" to Build.ID
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        InfoSection(
            title = "Hardware",
            items = listOf(
                "Processor" to Build.HARDWARE,
                "Board" to Build.BOARD,
                "Bootloader" to Build.BOOTLOADER
            )
        )
    }
}

@Composable
fun DeviceActionCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        elevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colors.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(text = subtitle, fontSize = 11.sp, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
        }
    }
}

@Composable
fun InfoSection(title: String, items: List<Pair<String, String>>) {
    Column {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.overline,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )
        Card(
            shape = RoundedCornerShape(16.dp),
            elevation = 0.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                items.forEachIndexed { index, (label, value) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = label, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                        Text(text = value, fontWeight = FontWeight.Bold)
                    }
                    if (index < items.size - 1) {
                        Divider(modifier = Modifier.padding(horizontal = 8.dp))
                    }
                }
            }
        }
    }
}
