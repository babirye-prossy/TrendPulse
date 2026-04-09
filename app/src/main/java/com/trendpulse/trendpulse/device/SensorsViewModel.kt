package com.trendpulse.trendpulse.device

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class SensorsViewModel(application: Application) : AndroidViewModel(application), SensorEventListener {

    private val sensorManager = application.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val _accelerometerData = MutableLiveData<String>("N/A")
    val accelerometerData: LiveData<String> = _accelerometerData

    private val _gyroscopeData = MutableLiveData<String>("N/A")
    val gyroscopeData: LiveData<String> = _gyroscopeData

    private val _proximityData = MutableLiveData<String>("N/A")
    val proximityData: LiveData<String> = _proximityData

    private val _lightData = MutableLiveData<String>("N/A")
    val lightData: LiveData<String> = _lightData

    private val _allSensors = MutableLiveData<List<Sensor>>()
    val allSensors: LiveData<List<Sensor>> = _allSensors

    init {
        _allSensors.value = sensorManager.getSensorList(Sensor.TYPE_ALL)
    }

    fun startListening() {
        registerSensor(Sensor.TYPE_ACCELEROMETER)
        registerSensor(Sensor.TYPE_GYROSCOPE)
        registerSensor(Sensor.TYPE_PROXIMITY)
        registerSensor(Sensor.TYPE_LIGHT)
    }

    private fun registerSensor(type: Int) {
        sensorManager.getDefaultSensor(type)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
    }

    private val _accelerometerValues = MutableLiveData<FloatArray>()
    val accelerometerValues: LiveData<FloatArray> = _accelerometerValues

    private val _gyroscopeValues = MutableLiveData<FloatArray>()
    val gyroscopeValues: LiveData<FloatArray> = _gyroscopeValues

    private val _proximityValue = MutableLiveData<Float>()
    val proximityValue: LiveData<Float> = _proximityValue

    private val _lightValue = MutableLiveData<Float>()
    val lightValue: LiveData<Float> = _lightValue

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                _accelerometerValues.value = event.values.copyOf()
                _accelerometerData.value = "X: ${"%.1f".format(event.values[0])}, Y: ${"%.1f".format(event.values[1])}, Z: ${"%.1f".format(event.values[2])}"
            }
            Sensor.TYPE_GYROSCOPE -> {
                _gyroscopeValues.value = event.values.copyOf()
                _gyroscopeData.value = "X: ${"%.1f".format(event.values[0])}, Y: ${"%.1f".format(event.values[1])}, Z: ${"%.1f".format(event.values[2])}"
            }
            Sensor.TYPE_PROXIMITY -> {
                _proximityValue.value = event.values[0]
                _proximityData.value = "Distance: ${event.values[0]}"
            }
            Sensor.TYPE_LIGHT -> {
                _lightValue.value = event.values[0]
                _lightData.value = "Lux: ${event.values[0]}"
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onCleared() {
        super.onCleared()
        stopListening()
    }
}
