package com.trendpulse.trendpulse.device

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import com.trendpulse.trendpulse.R
import com.trendpulse.trendpulse.notification.NotificationHelper

class DeviceFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                DeviceScreen(
                    onDynamicInfoClick = { replaceFragment(DynamicInfoFragment()) },
                    onSensorsClick = { replaceFragment(SensorsFragment()) },
                    onNotifyClick = {
                        NotificationHelper(requireContext()).showSimpleNotification(
                            "TrendPulse Alert",
                            "This is a test local notification!"
                        )
                    }
                )
            }
        }
    }

    private fun replaceFragment(fragment: Fragment) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .addToBackStack("device_main")
            .commit()
    }
}
