package com.trendpulse.trendpulse.core.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.trendpulse.trendpulse.core.viewmodel.CommentViewModel

/**
 * Fragment responsible for displaying sentiment analysis results.
 *
 * This fragment shares the same [CommentViewModel] instance with [CommentsFragment],
 * allowing seamless data transfer without the need for explicit arguments.
 *
 * It renders the [ResultsScreen] composable, which visualizes:
 * - Overall sentiment summary
 * - Sentiment distribution
 * - Trend analysis over time
 * - Per-comment sentiment breakdown
 */
class ResultsFragment : Fragment(){

    // ✅ activityViewModels shares the SAME ViewModel instance as CommentsFragment
    // So sentimentMap populated in CommentsFragment is visible here instantly
    private val vm: CommentViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                ResultsScreen(vm = vm)
            }
        }

    }
}

