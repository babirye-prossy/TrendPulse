package com.trendpulse.trendpulse.core.ml

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.exp

class SentimentAnalyzer private constructor(private val context: Context) {

    private val mutex = Mutex()

    private var interpreter: Interpreter? = null
    private lateinit var tokenizer: WordPieceTokenizer
    private var labels: List<String> = emptyList()

    private var attentionMaskIndex: Int = 0
    private var inputIdsIndex: Int = 1
    private var tokenTypeIdsIndex: Int = 2
    private var outputIndex: Int = 0

    init {
        try {
            tokenizer = WordPieceTokenizer(
                context = context,
                vocabAssetName = "vocab.txt",
                maxSeqLen = 128,
                doLowerCase = true
            )

            labels = loadLabels("labels.txt")

            val modelBuffer = loadModelFile(context, "mobilebert_sentiment.tflite")
            interpreter = Interpreter(modelBuffer)

            val localInterpreter = interpreter!!

            for (i in 0 until localInterpreter.inputTensorCount) {
                val tensor = localInterpreter.getInputTensor(i)
                val name = tensor.name()
                when {
                    "attention_mask" in name -> attentionMaskIndex = i
                    "input_ids" in name -> inputIdsIndex = i
                    "token_type_ids" in name -> tokenTypeIdsIndex = i
                }
                Log.i("SentimentAnalyzer", "Input[$i] ${tensor.name()} ${tensor.shape().contentToString()}")
            }

            val outTensor = localInterpreter.getOutputTensor(0)
            outputIndex = 0
            Log.i("SentimentAnalyzer", "Output[0] ${outTensor.name()} ${outTensor.shape().contentToString()}")

            // Fixed shape once for app lifetime since tokenizer always uses maxSeqLen=128
            localInterpreter.resizeInput(attentionMaskIndex, intArrayOf(1, 128))
            localInterpreter.resizeInput(inputIdsIndex, intArrayOf(1, 128))
            localInterpreter.resizeInput(tokenTypeIdsIndex, intArrayOf(1, 128))
            localInterpreter.allocateTensors()

            Log.i("SentimentAnalyzer", "Tokenizer + model + labels loaded successfully")
        } catch (e: Exception) {
            Log.e("SentimentAnalyzer", "Failed to initialize analyzer: ${e.message}", e)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: SentimentAnalyzer? = null

        fun getInstance(context: Context): SentimentAnalyzer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SentimentAnalyzer(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    suspend fun analyze(text: String): SentimentResult = withContext(Dispatchers.Default) {
        mutex.withLock {
            val localInterpreter = interpreter
                ?: return@withContext SentimentResult("UNKNOWN", 0f)

            return@withContext try {
                val bertInput = tokenizer.encode(text)

                val inputIdsBatch = arrayOf(bertInput.inputIds)
                val attentionMaskBatch = arrayOf(bertInput.attentionMask)
                val tokenTypeIdsBatch = arrayOf(bertInput.tokenTypeIds)

                val inputs = arrayOfNulls<Any>(3)
                inputs[attentionMaskIndex] = attentionMaskBatch
                inputs[inputIdsIndex] = inputIdsBatch
                inputs[tokenTypeIdsIndex] = tokenTypeIdsBatch

                val output = Array(1) { FloatArray(labels.size.coerceAtLeast(2)) }
                val outputs = mutableMapOf<Int, Any>(outputIndex to output)

                localInterpreter.runForMultipleInputsOutputs(inputs, outputs)

                val logits = output[0]
                val probs = softmax(logits)

                val safeLabels = if (labels.size == probs.size) labels else List(probs.size) { "CLASS_$it" }

                val scores = safeLabels.mapIndexed { idx, label ->
                    label.uppercase() to probs[idx]
                }.toMap()

                val best = scores.maxByOrNull { it.value }
                val bestLabel = best?.key ?: "UNKNOWN"
                val bestScore = best?.value ?: 0f

                Log.d("SentimentAnalyzer", "text=$text")
                Log.d("SentimentAnalyzer", "scores=$scores")
                Log.d("SentimentAnalyzer", "best=$bestLabel score=$bestScore")

                SentimentResult(
                    label = bestLabel,
                    score = bestScore
                )
            } catch (e: Exception) {
                Log.e("SentimentAnalyzer", "Inference failed: ${e.message}", e)
                SentimentResult("UNKNOWN", 0f)
            }
        }
    }

    fun close() {
        synchronized(this) {
            interpreter?.close()
            interpreter = null
            Log.i("SentimentAnalyzer", "Analyzer closed")
        }
    }

    private fun loadLabels(assetName: String): List<String> {
        val result = mutableListOf<String>()
        context.assets.open(assetName).use { input ->
            BufferedReader(InputStreamReader(input)).useLines { lines ->
                lines.forEach { line ->
                    val value = line.trim()
                    if (value.isNotEmpty()) result.add(value)
                }
            }
        }
        return result
    }

    private fun softmax(logits: FloatArray): FloatArray {
        val max = logits.maxOrNull() ?: 0f
        val exps = logits.map { exp((it - max).toDouble()).toFloat() }
        val sum = exps.sum().takeIf { it != 0f } ?: 1f
        return exps.map { it / sum }.toFloatArray()
    }

    private fun loadModelFile(context: Context, assetName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(assetName)
        FileInputStream(fileDescriptor.fileDescriptor).use { inputStream ->
            val fileChannel = inputStream.channel
            return fileChannel.map(
                FileChannel.MapMode.READ_ONLY,
                fileDescriptor.startOffset,
                fileDescriptor.declaredLength
            )
        }
    }
}

data class SentimentResult(
    val label: String,
    val score: Float,
)