package com.trendpulse.trendpulse.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trendpulse.trendpulse.core.viewmodel.CommentViewModel

/**
 * Composable function for presenting sentiment analysis results.
 */
@Composable
fun ResultsScreen(vm: CommentViewModel) {

    val sentimentMap by vm.sentimentMap.collectAsState()
    val overallSentiment by vm.overallSentiment.collectAsState()
    val scrapingState by vm.scrapingState.collectAsState()
    val sentimentTrend by vm.sentimentTrend.collectAsState()
    val searchQuery by vm.searchQuery.collectAsState()
    val searchResults by vm.searchResults.collectAsState()

    val positiveCount = sentimentMap.values.count { it.label == "POSITIVE" }
    val negativeCount = sentimentMap.values.count { it.label == "NEGATIVE" }
    val total = sentimentMap.size

    // --- Placeholder before any analysis ---
    if (scrapingState is CommentViewModel.ScrapingState.Idle) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Analyze a video first to see results",
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                style = MaterialTheme.typography.body1
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // --- Title ---
        item {
            Text(
                text = "Sentiment Results",
                style = MaterialTheme.typography.h6,
                fontWeight = FontWeight.Bold
            )
        }

        // --- Overall Summary Card ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = 4.dp,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = overallSentiment,
                        style = MaterialTheme.typography.h6
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$total comments analyzed",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }

        // --- Positive / Negative Bars ---
        if (total > 0) {
            item {
                val posRatio = positiveCount.toFloat() / total

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = 2.dp,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Breakdown",
                            style = MaterialTheme.typography.subtitle2,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Positive bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("😊", modifier = Modifier.width(32.dp))
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(20.dp)
                                    .background(Color(0xFFE0E0E0), RoundedCornerShape(10.dp))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(posRatio)
                                        .background(Color(0xFF4CAF50), RoundedCornerShape(10.dp))
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "$positiveCount",
                                fontSize = 12.sp,
                                modifier = Modifier.width(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Negative bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("😠", modifier = Modifier.width(32.dp))
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(20.dp)
                                    .background(Color(0xFFE0E0E0), RoundedCornerShape(10.dp))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(1f - posRatio)
                                        .background(Color(0xFFF44336), RoundedCornerShape(10.dp))
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "$negativeCount",
                                fontSize = 12.sp,
                                modifier = Modifier.width(24.dp)
                            )
                        }
                    }
                }
            }
        }

        // --- Sentiment Trend Chart ---
        if (sentimentTrend.size >= 2) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = 2.dp,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Sentiment Trend",
                            style = MaterialTheme.typography.subtitle2,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "😠 Negative",
                                style = MaterialTheme.typography.caption,
                                color = Color(0xFFF44336)
                            )
                            Text(
                                "😊 Positive",
                                style = MaterialTheme.typography.caption,
                                color = Color(0xFF4CAF50)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        SentimentLineChart(
                            dataPoints = sentimentTrend,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "First comment",
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                            )
                            Text(
                                "Latest comment",
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }

        // --- Search Input ---
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { vm.onSearchQueryChanged(it) },
                label = { Text("Search keywords & sentiment...") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
        }

        // --- Per-comment breakdown (Filtered if searching) ---
        item {
            Text(
                text = if (searchQuery.isBlank()) "Comment Breakdown" else "Search Results",
                style = MaterialTheme.typography.subtitle2,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (searchQuery.isNotBlank()) {
            // ✅ Show Search Results with Sentiment
            items(searchResults) { comment ->
                val result = sentimentMap[comment.text]
                SentimentCommentItem(text = comment.text, result = result)
            }
        } else if (sentimentMap.isEmpty()) {
            item {
                Text(
                    text = "Scroll through comments to analyze them",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                )
            }
        } else {
            items(sentimentMap.entries.toList()) { (text, result) ->
                SentimentCommentItem(text = text, result = result)
            }
        }
    }
}

@Composable
fun SentimentCommentItem(text: String, result: com.trendpulse.trendpulse.core.ml.SentimentResult?) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when (result?.label) {
                    "POSITIVE" -> "😊"
                    "NEGATIVE" -> "😠"
                    else -> "⏳"
                },
                modifier = Modifier.width(32.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.body2,
                modifier = Modifier.weight(1f),
                maxLines = 2
            )
            Spacer(modifier = Modifier.width(8.dp))
            if (result != null) {
                Text(
                    text = "${(result.score * 100).toInt()}%",
                    style = MaterialTheme.typography.caption,
                    color = if (result.label == "POSITIVE")
                        Color(0xFF4CAF50) else Color(0xFFF44336),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Divider()
    }
}
