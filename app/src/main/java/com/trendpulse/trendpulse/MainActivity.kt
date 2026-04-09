package com.trendpulse.trendpulse

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.trendpulse.trendpulse.core.ui.CommentsFragment
import com.trendpulse.trendpulse.core.ui.ResultsFragment
import com.trendpulse.trendpulse.core.viewmodel.CommentViewModel
import com.trendpulse.trendpulse.device.DeviceFragment
import com.trendpulse.trendpulse.location.MapFragment
import com.trendpulse.trendpulse.test.SentimentTestFragment

/**
 * Main entry point of the TrendPulse Android application.
 */
class MainActivity : AppCompatActivity() {

    private val commentViewModel: CommentViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "onCreate")
        setContentView(R.layout.activity_main)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)

        // Default screen
        if (savedInstanceState == null) {
            loadFragment(CommentsFragment())
        }

        handleIntent(intent)

        bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_comments -> CommentsFragment()
                R.id.nav_graphs -> ResultsFragment()
                R.id.nav_device -> DeviceFragment()
                R.id.nav_map -> MapFragment()
                R.id.nav_test -> SentimentTestFragment()
                else -> return@setOnItemSelectedListener false
            }
            loadFragment(fragment)
            true
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val appLinkData = intent?.data
        
        // Handle Deep Links (ACTION_VIEW)
        if (Intent.ACTION_VIEW == intent?.action && appLinkData != null) {
            val videoUrl = appLinkData.getQueryParameter("url")
            if (!videoUrl.isNullOrBlank()) {
                processSharedUrl(videoUrl)
            } else {
                // FALLBACK: Load last analyzed post if no URL is provided
                Log.d("TrendPulse", "Deep link with no URL, loading last post...")
                val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
                bottomNav.selectedItemId = R.id.nav_graphs
                loadFragment(ResultsFragment())
                commentViewModel.loadLastPost()
            }
        } 
        // Handle Share Sheet (ACTION_SEND)
        else if (Intent.ACTION_SEND == intent?.action && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { sharedText ->
                // Extract URL from shared text (handles cases where text + URL are shared)
                val urlRegex = "(https?://[^\\s]+)".toRegex()
                val match = urlRegex.find(sharedText)
                match?.value?.let { extractedUrl ->
                    processSharedUrl(extractedUrl)
                }
            }
        }
    }

    private fun processSharedUrl(url: String) {
        Log.d("TrendPulse", "Processing URL: $url")
        // Switch to results tab
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNav.selectedItemId = R.id.nav_graphs
        loadFragment(ResultsFragment())
        
        // Start analysis
        commentViewModel.startScraping(url)
    }

    override fun onStart() {
        super.onStart()
        Log.d("MainActivity", "onStart")
    }

    override fun onResume() {
        super.onResume()
        Log.d("MainActivity", "onResume")
    }

    override fun onPause() {
        super.onPause()
        Log.d("MainActivity", "onPause")
    }

    override fun onStop() {
        super.onStop()
        Log.d("MainActivity", "onStop")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("MainActivity", "onDestroy")
    }
}
