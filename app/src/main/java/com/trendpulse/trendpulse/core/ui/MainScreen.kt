package com.trendpulse.trendpulse.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.LoadState
import com.trendpulse.trendpulse.core.viewmodel.CommentViewModel

/**
 * Composable function representing the main user interface for comment analysis.
 */
@Composable
fun MainScreen(
    vm: CommentViewModel = viewModel(),
    onAnalyze: (String) -> Unit = {}
) {
    var url by remember { mutableStateOf("") }
    var pagingHasStarted by remember { mutableStateOf(false) }

    val comments = vm.comments.collectAsLazyPagingItems()
    val scrapingState by vm.scrapingState.collectAsState()

    if (comments.loadState.refresh is LoadState.Loading) pagingHasStarted = true

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

        // --- URL Input ---
        TextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Paste post URL") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // --- Analyze Button ---
        Button(
            onClick = {
                vm.startScraping(url)
                onAnalyze(url)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Analyze")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- Scraping State Banner ---
        when (val state = scrapingState) {
            is CommentViewModel.ScrapingState.Loading -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(4.dp))
                Text(state.message, style = MaterialTheme.typography.caption)
            }
            is CommentViewModel.ScrapingState.Error -> {
                Text(
                    text = "Error: ${state.message}",
                    color = MaterialTheme.colors.error,
                    style = MaterialTheme.typography.caption
                )
            }
            else -> {}
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- Comment List ---
        if (scrapingState is CommentViewModel.ScrapingState.Done) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(count = comments.itemCount) { index ->
                    val comment = comments[index]
                    comment?.let {
                        CommentItem(user = it.user, text = it.text)
                    }
                }

                // --- Paging Load States ---
                comments.apply {
                    when {
                        loadState.append is LoadState.Loading -> {
                            item {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                        .wrapContentWidth()
                                )
                            }
                        }
                        loadState.refresh is LoadState.NotLoading && itemCount == 0 && pagingHasStarted -> {
                            item {
                                Text(
                                    text = "No comments found",
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                        loadState.refresh is LoadState.Error -> {
                            val e = (loadState.refresh as LoadState.Error).error
                            item {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Failed to load: ${e.message}",
                                        color = MaterialTheme.colors.error
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(onClick = { comments.retry() }) {
                                        Text("Retry")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CommentItem(user: String, text: String, isSearchResult: Boolean = false) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .background(if (isSearchResult) Color.Yellow.copy(alpha = 0.1f) else Color.Transparent)
    ) {
        Text(
            text = user,
            style = MaterialTheme.typography.subtitle2,
            color = MaterialTheme.colors.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.body1
        )
        Spacer(modifier = Modifier.height(8.dp))
        Divider()
    }
}
