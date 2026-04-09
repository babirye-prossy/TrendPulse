package com.trendpulse.trendpulse.device

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.io.File

class DeviceViewModel(application: Application) : AndroidViewModel(application) {

    private val _batteryStatus = MutableLiveData<String>()
    val batteryStatus: LiveData<String> = _batteryStatus

    private val _networkStatus = MutableLiveData<String>()
    val networkStatus: LiveData<String> = _networkStatus

    private val _memoryStatus = MutableLiveData<String>()
    val memoryStatus: LiveData<String> = _memoryStatus

    private val _storageStatus = MutableLiveData<String>()
    val storageStatus: LiveData<String> = _storageStatus

    private val _cpuStatus = MutableLiveData<String>()
    val cpuStatus: LiveData<String> = _cpuStatus

    private val _broadcastLogs = MutableLiveData<List<String>>(emptyList())
    val broadcastLogs: LiveData<List<String>> = _broadcastLogs

    private val _networkDetails = MutableLiveData<Map<String, String>>()
    val networkDetails: LiveData<Map<String, String>> = _networkDetails

    private val _cpuActivity = MutableLiveData<List<Int>>()
    val cpuActivity: LiveData<List<Int>> = _cpuActivity

    fun updateDynamicStats() {
        val context = getApplication<Application>().applicationContext

        // Battery
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        _batteryStatus.value = "Battery: $level%"

        // Network
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val nw = cm.activeNetwork
        val actNw = cm.getNetworkCapabilities(nw)
        
        val details = mutableMapOf<String, String>()
        val connectionType = when {
            actNw == null -> "None"
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            else -> "Other"
        }
        details["Type"] = connectionType
        details["Status"] = if (actNw?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true) "Online" else "Offline"
        
        // IP Address
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        details["IP"] = addr.hostAddress ?: "Unknown"
                    }
                }
            }
        } catch (e: Exception) {
            details["IP"] = "Unavailable"
        }
        
        // SSID (Requires permissions, showing placeholder/status)
        details["SSID"] = if (connectionType == "WiFi") "Connected" else "N/A"
        
        _networkDetails.value = details
        _networkStatus.value = "Network: $connectionType"

        // Memory
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)
        _memoryStatus.value = "RAM: ${memInfo.availMem / (1024 * 1024)}MB / ${memInfo.totalMem / (1024 * 1024)}MB"

        // Storage
        val path: File = Environment.getDataDirectory()
        val stat = StatFs(path.path)
        val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
        _storageStatus.value = "Storage: ${availableBytes / (1024 * 1024)}MB available"

        // CPU Activity (Simulated for visualization)
        val coreCount = Runtime.getRuntime().availableProcessors()
        _cpuStatus.value = "CPU Cores: $coreCount"
        _cpuActivity.value = List(coreCount) { (10..90).random() }
    }

    fun addBroadcastLog(message: String) {
        val currentLogs = _broadcastLogs.value ?: emptyList()
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        _broadcastLogs.value = listOf("[$timestamp] $message") + currentLogs
    }
}
