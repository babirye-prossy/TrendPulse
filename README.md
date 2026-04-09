# TrendPulse — Interview Questions Implementation Guide

---

## Q1 — Paging 3 + TikTok Comments + LazyColumn + Retry Logic

> Implement a Paging 3 data source that loads paginated TikTok comment data from a REST API and displays it in a Compose LazyColumn. How do you handle network errors and implement retry logic within Paging 3?

### Architecture

```
User taps Analyze
       │
       ▼
CommentViewModel.startScraping(url)
  └── polls /comments every 4s until stage = "done"
       │
       ▼
_activeUrl emits → flatMapLatest → Pager → CommentPagingSource
       │
       ▼
LazyColumn ← collectAsLazyPagingItems() ← PagingData<Comment>
```

### Key Classes & Functions

#### `CommentPagingSource`
Extends `PagingSource<Int, Comment>`. Loads one page at a time from the REST API.

```kotlin
override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Comment> {
    val page = params.key ?: 1
    return try {
        val response = apiService.getComments(tiktokUrl, page, params.loadSize)
        val comments = response.comments ?: emptyList()
        LoadResult.Page(
            data = comments,
            prevKey = if (page == 1) null else page - 1,
            nextKey = if (comments.size < params.loadSize) null else page + 1
        )
    } catch (e: Exception) {
        LoadResult.Error(e)
    }
}
```

**Pagination stop condition:** `comments.size < params.loadSize` — stops when the server returns fewer items than requested, indicating the last page.

#### `getRefreshKey`
Returns the page key closest to the last viewed item so the list resumes correctly on refresh.

```kotlin
override fun getRefreshKey(state: PagingState<Int, Comment>): Int? {
    return state.anchorPosition?.let { anchor ->
        state.closestPageToPosition(anchor)?.prevKey?.plus(1)
            ?: state.closestPageToPosition(anchor)?.nextKey?.minus(1)
    }
}
```

#### `CommentViewModel.startScraping()`
Polls the server every 4 seconds until `stage != "collecting"` before triggering the pager. This keeps `PagingSource` clean — it only runs when data is confirmed ready.

```kotlin
fun startScraping(url: String) {
    viewModelScope.launch {
        _scrapingState.value = ScrapingState.Loading("Starting scrape...")
        apiService.startScraping(mapOf("tiktokUrl" to url))
        var stage = "collecting"
        var attempt = 0
        while (stage == "collecting" && attempt < 30) {
            delay(4_000)
            attempt++
            val check = apiService.getComments(url, 1, 1)
            stage = check.stage ?: "collecting"
            if (stage == "collecting") {
                _scrapingState.value = ScrapingState.Loading(
                    "Scraping comments... (${attempt * 4}s)"
                )
            }
        }
        _scrapingState.value = ScrapingState.Done
        _activeUrl.value = null  // reset so flatMapLatest always fires
        _activeUrl.value = url
    }
}
```

#### Stable `comments` Flow
Rebuilt only when `_activeUrl` changes. `cachedIn` survives recomposition.

```kotlin
val comments: Flow<PagingData<Comment>> = _activeUrl
    .filterNotNull()
    .flatMapLatest { url ->
        Pager(
            config = PagingConfig(pageSize = 10, enablePlaceholders = false),
            pagingSourceFactory = { CommentPagingSource(apiService, url) }
        ).flow
    }
    .cachedIn(viewModelScope)
```

### Error Handling & Retry

`LoadResult.Error(e)` is returned on any exception. Paging 3 surfaces this via `loadState.refresh is LoadState.Error`. The UI shows a Retry button calling `comments.retry()`:

```kotlin
loadState.refresh is LoadState.Error -> {
    val e = (loadState.refresh as LoadState.Error).error
    item {
        Column {
            Text("Failed to load: ${e.message}", color = MaterialTheme.colors.error)
            Button(onClick = { comments.retry() }) { Text("Retry") }
        }
    }
}
```

### Paging 3 APIs Used

| API | Role |
|---|---|
| `PagingSource<Int, Comment>` | Defines how to load one page |
| `Pager` | Builds the `PagingData` flow |
| `PagingConfig` | Sets `pageSize`, disables placeholders |
| `collectAsLazyPagingItems()` | Converts Flow to Compose-observable items |
| `LazyPagingItems.loadState` | Exposes refresh / append / prepend states |
| `LoadState.Loading / NotLoading / Error` | Drives UI feedback |
| `lazyPagingItems.retry()` | Retries last failed load |
| `cachedIn(viewModelScope)` | Survives recomposition |
| `flatMapLatest` | Re-creates paging source on URL change |

### `ScrapingState` Sealed Class

| State | When | UI |
|---|---|---|
| `Idle` | App launch | Nothing shown |
| `Loading(message)` | Polling in progress | Progress bar + live timer |
| `Done` | Data confirmed ready | LazyColumn mounts |
| `Error(message)` | Timeout or exception | Error text shown |

---

## Q2 — On-Device Sentiment Classification with TFLite BERT

> Run on-device sentiment classification using a quantized TensorFlow Lite BERT model. How do you tokenize input text on Android and manage inference latency so it doesn't block the scrolling list?

### Model Preparation (Python)

```python
from transformers import TFAutoModelForSequenceClassification
import tensorflow as tf

model = TFAutoModelForSequenceClassification.from_pretrained("mobilebert_tf")
converter = tf.lite.TFLiteConverter.from_saved_model("saved_model")
converter.optimizations = [tf.lite.Optimize.DEFAULT]
tflite_model = converter.convert()

with open("mobilebert_sentiment.tflite", "wb") as f:
    f.write(tflite_model)
```

Place `mobilebert_sentiment.tflite` and `vocab.txt` in `app/src/main/assets/`.

### Why Raw `Interpreter` Instead of `BertNLClassifier`

`BertNLClassifier` from the Task Library requires TFLite metadata embedded in the model file. The HuggingFace to TFLiteConverter path strips metadata. Raw `Interpreter` works with any `.tflite` file regardless of metadata.

### Key Classes

#### `WordpieceTokenizer`
Loads `vocab.txt` from assets and converts text to `input_ids` and `attention_mask` arrays matching the model's `[1, 128]` input shape.

```kotlin
fun tokenize(text: String): TokenizerOutput {
    val tokens = text.lowercase()
        .split(Regex("\\s+|(?=[^a-z0-9])|(?<=[^a-z0-9])"))
        .filter { it.isNotBlank() }
        .flatMap { wordpieceTokenize(it) }
    val truncated = tokens.take(MAX_LEN - 2)
    val ids = mutableListOf(clsId)
    ids.addAll(truncated.map { vocab[it] ?: unkId })
    ids.add(sepId)
    val inputIds = IntArray(MAX_LEN) { i -> if (i < ids.size) ids[i] else padId }
    val attentionMask = IntArray(MAX_LEN) { i -> if (i < ids.size) 1 else 0 }
    return TokenizerOutput(inputIds, attentionMask)
}
```

#### `SentimentAnalyzer`

```kotlin
// Pre-warmed at ViewModel creation — no cold start on first comment
private val interpreter: Interpreter by lazy {
    val assetFileDescriptor = context.assets.openFd("mobilebert_sentiment.tflite")
    val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
    val mappedBuffer = inputStream.channel.map(
        FileChannel.MapMode.READ_ONLY,
        assetFileDescriptor.startOffset,
        assetFileDescriptor.declaredLength
    )
    Interpreter(mappedBuffer, Interpreter.Options().apply { numThreads = 2 })
}

// Dispatchers.Default — never blocks scroll or UI thread
suspend fun analyze(text: String): SentimentResult = withContext(Dispatchers.Default) {
    val tokens = tokenizer.tokenize(text)
    val inputIds = Array(1) { tokens.inputIds }
    val attentionMask = Array(1) { tokens.attentionMask }
    val output = Array(1) { FloatArray(2) }
    interpreter.runForMultipleInputsOutputs(
        arrayOf(inputIds, attentionMask),
        mapOf(0 to output)
    )
    val expNeg = Math.exp(output[0][0].toDouble()).toFloat()
    val expPos = Math.exp(output[0][1].toDouble()).toFloat()
    val sum = expNeg + expPos
    if (expPos / sum >= expNeg / sum)
        SentimentResult("POSITIVE", expPos / sum)
    else
        SentimentResult("NEGATIVE", expNeg / sum)
}
```

### Latency Management

Inference runs on `Dispatchers.Default` via `withContext`. The UI triggers inference only as each comment scrolls into view via `LaunchedEffect`:

```kotlin
items(count = comments.itemCount) { index ->
    val comment = comments[index]
    comment?.let {
        LaunchedEffect(it.text) {
            vm.analyzeSentiment(it)  // fires once per comment, never repeated
        }
        CommentItem(user = it.user, text = it.text, sentiment = sentimentMap[it.text])
    }
}
```

Results accumulate in `_sentimentMap: MutableStateFlow<Map<String, SentimentResult>>` and the overall summary updates reactively.

### TFLite APIs Used

| API | Role |
|---|---|
| `Interpreter` | Runs raw TFLite model inference |
| `Interpreter.Options.numThreads` | Controls CPU parallelism |
| `runForMultipleInputsOutputs` | Feeds `input_ids` + `attention_mask` together |
| `Dispatchers.Default` | Runs inference off the main thread |
| `StateFlow` | Holds per-comment and overall sentiment results |

---

## Q2.5 — Canvas-Based Sentiment Trend Chart (No Third-Party Library)

> Design a charting screen in Jetpack Compose that displays a sentiment trend over time as a line chart. How do you implement a custom Canvas-based chart without a third-party library?

### Data Model

```kotlin
data class SentimentTrendPoint(
    val index: Int,    // comment number (x axis)
    val score: Float   // 0.0 = negative, 1.0 = positive (y axis)
)
```

### `SentimentLineChart` Composable

```kotlin
@Composable
fun SentimentLineChart(dataPoints: List<SentimentTrendPoint>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val paddingPx = 24.dp.toPx()
        val chartWidth = size.width - paddingPx * 2
        val chartHeight = size.height - paddingPx * 2
        val count = dataPoints.size

        // Normalize data points to pixel coordinates
        fun xOf(index: Int) = paddingPx + (index.toFloat() / (count - 1)) * chartWidth
        fun yOf(score: Float) = paddingPx + (1f - score) * chartHeight

        val linePath = Path().apply {
            dataPoints.forEachIndexed { i, point ->
                if (i == 0) moveTo(xOf(i), yOf(point.score))
                else lineTo(xOf(i), yOf(point.score))
            }
        }

        val fillPath = Path().apply {
            addPath(linePath)
            lineTo(xOf(count - 1), paddingPx + chartHeight)
            lineTo(xOf(0), paddingPx + chartHeight)
            close()
        }

        // drawPath with Brush.verticalGradient for fill below the line
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF4CAF50).copy(alpha = 0.4f), Color.Transparent),
                startY = paddingPx, endY = paddingPx + chartHeight
            )
        )

        // drawPath for the line itself
        drawPath(path = linePath, color = Color(0xFF4CAF50),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        // drawLine for dashed neutral midline at 0.5
        drawLine(color = Color.Gray.copy(alpha = 0.4f),
            start = Offset(paddingPx, yOf(0.5f)),
            end = Offset(paddingPx + chartWidth, yOf(0.5f)),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))

        // drawCircle for dots at each data point
        dataPoints.forEachIndexed { i, point ->
            drawCircle(Color.White, radius = 5.dp.toPx(), center = Offset(xOf(i), yOf(point.score)))
            drawCircle(
                if (point.score >= 0.5f) Color(0xFF4CAF50) else Color(0xFFF44336),
                radius = 3.dp.toPx(), center = Offset(xOf(i), yOf(point.score))
            )
        }
    }
}
```

### Canvas APIs Used

| API | Role |
|---|---|
| `Canvas { }` composable | Drawing surface that recomposes with state |
| `drawLine()` | Grid lines + neutral midline |
| `drawPath()` | Line path + gradient fill |
| `Brush.verticalGradient()` | Fade below the line |
| `PathEffect.dashPathEffect()` | Dashed midline |
| `.dp.toPx()` | Pixel-perfect rendering via density |
| `StrokeCap.Round / StrokeJoin.Round` | Smooth line rendering |

### Live Updates
`sentimentTrend` is a `StateFlow<List<SentimentTrendPoint>>` appended to in `analyzeSentiment()`. Every new inference triggers a recomposition of `SentimentLineChart` automatically.

---

## Q3 — WebSocket with Foreground Service + Exponential Backoff Reconnect

> The analytics dashboard must support real-time updates as new sentiment data is processed. How would you implement a WebSocket connection in Android that survives app backgrounding and reconnects automatically?

### Architecture

```
User taps Analyze
       │
       ├── REST: POST /collect → triggers Apify scrape
       └── Android: startForegroundService(WebSocketService)
                          │
                          ▼
              OkHttp WebSocket → wss://tikgrub.onrender.com/?url=...
                          │
                   WebSocketListener
                          │
              ┌──────────┴──────────┐
           onMessage            onFailure
              │                     │
         emit to               scheduleReconnect()
      eventFlow              (exponential backoff)
              │
              ▼
    WebSocketService.eventFlow (MutableSharedFlow)
              │
              ▼
    CommentViewModel.init { collect }
              │
         ┌────┴────┐
    liveComments  analyzeSentiment()
```

### Server Side

WebSocket server shares the same port as Express using `createServer`:

```javascript
import { WebSocketServer } from 'ws';
import { createServer } from 'http';

const server = createServer(app);
const wss = new WebSocketServer({ server });
const clients = {};

wss.on('connection', (ws, req) => {
    const urlParam = new URL(req.url, `http://localhost`).searchParams.get('url');
    if (!clients[urlParam]) clients[urlParam] = new Set();
    clients[urlParam].add(ws);
    ws.on('close', () => clients[urlParam]?.delete(ws));
});

function broadcast(tiktokUrl, payload) {
    clients[tiktokUrl]?.forEach(ws => {
        if (ws.readyState === 1) ws.send(JSON.stringify(payload));
    });
}
```

### `WebSocketEvent.kt`

```kotlin
sealed class WebSocketEvent {
    object Connected : WebSocketEvent()
    object Disconnected : WebSocketEvent()
    object ScrapeStarted : WebSocketEvent()
    data class NewComments(
        val comments: List<Comment>,  // reuses existing Comment model
        val progress: Int,
        val total: Int
    ) : WebSocketEvent()
    data class Done(val total: Int) : WebSocketEvent()
    data class Error(val message: String) : WebSocketEvent()
}
```

### `WebSocketService.kt` — Foreground Service

```kotlin
class WebSocketService : Service() {

    companion object {
        // Singleton SharedFlow — ViewModel collects from this
        val eventFlow = MutableSharedFlow<WebSocketEvent>(replay = 0, extraBufferCapacity = 64)
    }

    // START_STICKY — OS restarts service if killed
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        tiktokUrl?.let { connect(it) }
        return START_STICKY
    }

    private fun connect(url: String) {
        val request = Request.Builder().url("wss://tikgrub.onrender.com/?url=${encode(url)}").build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                reconnectAttempts = 0
                emit(WebSocketEvent.Connected)
            }
            override fun onMessage(ws: WebSocket, text: String) {
                // parse JSON and emit appropriate WebSocketEvent
            }
            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                emit(WebSocketEvent.Disconnected)
                scheduleReconnect(url)
            }
        })
    }

    // Exponential backoff: 2s → 4s → 8s → 16s → max 30s
    private fun scheduleReconnect(url: String) {
        reconnectAttempts++
        val delayMs = minOf(2000L * (1 shl reconnectAttempts), 30_000L)
        Handler(Looper.getMainLooper()).postDelayed({ connect(url) }, delayMs)
    }
}
```

### `CommentViewModel.init` — Collecting from SharedFlow

```kotlin
init {
    viewModelScope.launch {
        WebSocketService.eventFlow.collect { event ->
            when (event) {
                is WebSocketEvent.NewComments -> {
                    _liveComments.update { it + event.comments }
                    event.comments.forEach { analyzeSentiment(it) }
                }
                is WebSocketEvent.Done -> {
                    _scrapingState.value = ScrapingState.Done
                    _activeUrl.value = null
                    _activeUrl.value = _activeUrl.value
                }
                is WebSocketEvent.Error -> _scrapingState.value = ScrapingState.Error(event.message)
                else -> {}
            }
        }
    }
}
```

### Why `SharedFlow` over `StateFlow`
`SharedFlow` with `replay=0` is correct for WebSocket events because they are one-shot emissions. `StateFlow` always replays the last value to new collectors, which would re-deliver old comments to new collectors.

### WebSocket APIs Used

| API | Role |
|---|---|
| `OkHttpClient.newWebSocket()` | Opens the WebSocket connection |
| `WebSocketListener` | Handles `onOpen`, `onMessage`, `onFailure`, `onClosed` |
| `Foreground Service` + `START_STICKY` | Keeps connection alive when backgrounded |
| Exponential backoff in `onFailure()` | Auto-reconnects: 2s to 4s to 8s to max 30s |
| `MutableSharedFlow` | Broadcasts events to ViewModel |
| `pingInterval(30s)` | Keep-alive pings to prevent connection drops |

### Manifest

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC"/>
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>

<service
    android:name=".core.network.WebSocketService"
    android:foregroundServiceType="dataSync"
    android:exported="false"/>
```

---

## Q4 — Room Caching with NetworkBoundResource Pattern

> The app processes thousands of comments per brand per day. How would you implement a caching strategy using Room as a local cache backed by a remote API, following the NetworkBoundResource pattern?

### Architecture

```
UI observes Repository
       │
       ▼
CommentRepository.getComments(url)
       │
       ▼
networkBoundResource {
    query  = Room DB Flow         ← single source of truth
    fetch  = Retrofit API call
    save   = write to Room        ← triggers DB Flow to re-emit
    should = cache empty?
}
       │
  ┌────┴────┐
  │         │
Loading   Success/Error
(DB data) (DB data after network write)
```

### Room Entities

```kotlin
@Entity(tableName = "comments")
data class CachedComment(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val tiktokUrl: String,
    val text: String,
    val user: String,
    val cachedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "sentiment")
data class CachedSentiment(
    @PrimaryKey val commentText: String,
    val tiktokUrl: String,
    val label: String,
    val score: Float,
    val cachedAt: Long = System.currentTimeMillis()
)
```

### DAOs

```kotlin
@Dao
interface CommentDao {
    // Flow — Room re-emits whenever table changes
    @Query("SELECT * FROM comments WHERE tiktokUrl = :url ORDER BY id ASC")
    fun getComments(url: String): Flow<List<CachedComment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(comments: List<CachedComment>)

    @Query("DELETE FROM comments WHERE tiktokUrl = :url")
    suspend fun deleteForUrl(url: String)
}
```

### `networkBoundResource` Extension

```kotlin
fun <Local, Remote> networkBoundResource(
    query: () -> Flow<Local>,
    fetch: suspend () -> Remote,
    saveFetchResult: suspend (Remote) -> Unit,
    shouldFetch: suspend (Local) -> Boolean = { true }
): Flow<Resource<Local>> = flow {

    // Step 1 — emit Loading with current DB data immediately (cache-first)
    emit(Resource.Loading(null))
    val localData = query().first()
    emit(Resource.Loading(localData))

    if (shouldFetch(localData)) {
        try {
            // Step 2 — fetch from network, write to DB
            val remote = fetch()
            saveFetchResult(remote)
            // Step 3 — DB write causes query() Flow to re-emit automatically
            emitAll(query().map { Resource.Success(it) })
        } catch (e: Exception) {
            // On failure — still show cached data alongside the error
            emitAll(query().map { Resource.Error(e.message ?: "Unknown error", it) })
        }
    } else {
        emitAll(query().map { Resource.Success(it) })
    }
}
```

### `Resource` Sealed Class

```kotlin
sealed class Resource<T>(val data: T? = null, val message: String? = null) {
    class Loading<T>(data: T? = null) : Resource<T>(data)
    class Success<T>(data: T) : Resource<T>(data)
    class Error<T>(message: String, data: T? = null) : Resource<T>(data, message)
}
```

### `CommentRepository`

```kotlin
class CommentRepository(private val apiService: ApiService, private val db: AppDatabase) {

    // UI never touches the network — only observes Room
    fun getComments(tiktokUrl: String): Flow<Resource<List<Comment>>> =
        networkBoundResource(
            query = {
                db.commentDao().getComments(tiktokUrl)
                    .map { it.map { c -> Comment(text = c.text, user = c.user) } }
            },
            fetch = { apiService.getComments(tiktokUrl, 1, 100) },
            saveFetchResult = { response ->
                db.commentDao().deleteForUrl(tiktokUrl)
                db.commentDao().insertAll(
                    response.comments.map {
                        CachedComment(tiktokUrl = tiktokUrl, text = it.text, user = it.user)
                    }
                )
            },
            shouldFetch = { cached -> cached.isEmpty() }
        )

    suspend fun saveSentiment(tiktokUrl: String, comment: Comment, result: SentimentResult) {
        db.sentimentDao().insertAll(listOf(
            CachedSentiment(
                commentText = comment.text, tiktokUrl = tiktokUrl,
                label = result.label, score = result.score
            )
        ))
    }

    fun getSentiment(tiktokUrl: String): Flow<List<CachedSentiment>> =
        db.sentimentDao().getSentiment(tiktokUrl)
}
```

### Cache Restoration in ViewModel

On app reopen, cached sentiment is restored without re-running inference:

```kotlin
fun loadCached(url: String) {
    viewModelScope.launch {
        repository.getSentiment(url).collect { cached ->
            val map = cached.associate { it.commentText to SentimentResult(it.label, it.score) }
            if (map.isNotEmpty()) {
                _sentimentMap.value = map
                _sentimentTrend.value = map.values.mapIndexed { i, r ->
                    SentimentTrendPoint(i, if (r.label == "POSITIVE") r.score else 1f - r.score)
                }
            }
        }
    }
}
```

### Room + NetworkBoundResource APIs Used

| API | Role |
|---|---|
| `@Entity` | Defines Room table schema |
| `@Dao` | Defines DB query/insert/delete operations |
| `@Database` | Room database singleton |
| `Flow<List<T>>` from DAO | Auto-notifies UI on any table change |
| `OnConflictStrategy.REPLACE` | Upsert behavior for cache refresh |
| `networkBoundResource` | Orchestrates cache-first + network-sync pattern |
| `Resource<T>` | Wraps Loading / Success / Error states |
| `emit(Resource.Loading(localData))` | Shows cached data immediately |
| `saveFetchResult` | Writes network data to Room |
| `emitAll(query().map {...})` | UI always reads from Room, never network |

---

## Project File Structure

```
app/src/main/java/com/trendpulse/trendpulse/
├── core/
│   ├── api/
│   │   ├── ApiService.kt
│   │   └── RetrofitClient.kt
│   ├── db/
│   │   ├── AppDatabase.kt
│   │   ├── CachedComment.kt
│   │   ├── CachedSentiment.kt
│   │   ├── CommentDao.kt
│   │   └── SentimentDao.kt
│   ├── ml/
│   │   ├── SentimentAnalyzer.kt
│   │   ├── SentimentResult.kt
│   │   └── WordpieceTokenizer.kt
│   ├── model/
│   │   ├── Comment.kt
│   │   ├── CommentResponse.kt
│   │   └── SentimentTrendPoint.kt
│   ├── network/
│   │   ├── WebSocketEvent.kt
│   │   └── WebSocketService.kt
│   ├── paging/
│   │   └── CommentPagingSource.kt
│   ├── repository/
│   │   └── CommentRepository.kt
│   ├── ui/
│   │   ├── CommentsFragment.kt
│   │   ├── MainScreen.kt
│   │   ├── ResultsFragment.kt
│   │   ├── ResultsScreen.kt
│   │   └── SentimentLineChart.kt
│   ├── util/
│   │   ├── NetworkBoundResource.kt
│   │   └── Resource.kt
│   └── viewmodel/
│       └── CommentViewModel.kt
├── MainActivity.kt
server/
└── index.js
assets/
├── mobilebert_sentiment.tflite
└── vocab.txt
```
