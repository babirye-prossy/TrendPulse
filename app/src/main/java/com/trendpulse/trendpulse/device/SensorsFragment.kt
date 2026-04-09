package com.trendpulse.trendpulse.device

import android.graphics.Color
import android.hardware.Sensor
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels

class SensorsFragment : Fragment() {

    private val viewModel: SensorsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                SensorsScreen(
                    viewModel = viewModel,
                    onBackClick = { parentFragmentManager.popBackStack() }
                )
            }
        }
    }
}
