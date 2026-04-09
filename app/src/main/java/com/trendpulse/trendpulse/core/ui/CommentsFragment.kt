package com.trendpulse.trendpulse.core.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.trendpulse.trendpulse.core.network.WebSocketService
import com.trendpulse.trendpulse.core.viewmodel.CommentViewModel

/**
 * Fragment responsible for displaying the main comment analysis interface.
 *
 * This component serves as the entry point for user interaction, allowing users
 * to input a post URL and initiate the scraping and analysis process.
 *
 * It integrates Jetpack Compose within a traditional Fragment using [ComposeView],
 * enabling modern declarative UI within an existing Android architecture.
 *
 * Responsibilities:
 * - Rendering the [MainScreen] composable
 * - Triggering the WebSocket foreground service for real-time updates
 * - Sharing a ViewModel instance across fragments using activity scope
 */
class CommentsFragment : Fragment() {

    private val vm: CommentViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                MainScreen(
                    vm = vm,
                    onAnalyze = { url ->
                        // ✅ Start WebSocket foreground service
                        val intent = Intent(requireContext(), WebSocketService::class.java)
                            .putExtra(WebSocketService.EXTRA_URL, url)
                        requireContext().startForegroundService(intent)
                    }
                )
            }
        }
    }
}