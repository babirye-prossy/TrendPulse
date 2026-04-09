package com.trendpulse.trendpulse.test

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.trendpulse.trendpulse.R
import com.trendpulse.trendpulse.core.ml.SentimentAnalyzer
import com.trendpulse.trendpulse.core.ml.SentimentResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.charset.Charset

class SentimentTestFragment : Fragment() {

    private lateinit var analyzer: SentimentAnalyzer
    private lateinit var rv: RecyclerView
    private lateinit var summary: TextView
    private lateinit var btn: Button

    private val adapter = TestResultAdapter()

    data class TestData(val text: String, val label: String)

    data class TestResult(
        val text: String,
        val expected: String,
        val actual: String,
        val score: Float,
        val isCorrect: Boolean
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val v = inflater.inflate(R.layout.fragment_sentiment_test, container, false)

        rv = v.findViewById(R.id.rvResults)
        summary = v.findViewById(R.id.tvSummary)
        btn = v.findViewById(R.id.btnRunTest)

        rv.layoutManager = LinearLayoutManager(context)
        rv.adapter = adapter

        analyzer = SentimentAnalyzer.getInstance(requireContext())

        btn.setOnClickListener {
            runFullDiagnostic()
        }

        return v
    }

    // 🔥 MAIN TEST RUNNER
    private fun runFullDiagnostic() {
        lifecycleScope.launch {
            summary.text = "Running diagnostics..."

            // 1️⃣ Model sanity test
            val sanity = runSanityCheck()

            // 2️⃣ Load dataset
            val dataset = loadTestData()

            // 3️⃣ Run evaluation
            val results = mutableListOf<TestResult>()

            var correct = 0
            var tp = 0; var tn = 0; var fp = 0; var fn = 0

            dataset.forEach {
                val clean = preprocess(it.text)

                val res = withContext(Dispatchers.Default) {
                    analyzer.analyze(clean)
                }

                val predicted = normalize(res.label)
                val expected = normalize(it.label)

                val isCorrect = predicted == expected
                if (isCorrect) correct++

                // confusion matrix
                when {
                    predicted == "POSITIVE" && expected == "POSITIVE" -> tp++
                    predicted == "NEGATIVE" && expected == "NEGATIVE" -> tn++
                    predicted == "POSITIVE" && expected == "NEGATIVE" -> fp++
                    predicted == "NEGATIVE" && expected == "POSITIVE" -> fn++
                }

                Log.d("TEST", "${it.text} -> $predicted (${res.score})")

                results.add(
                    TestResult(it.text, expected, predicted, res.score, isCorrect)
                )
            }

            adapter.submitList(results)

            val accuracy = (correct.toFloat() / dataset.size) * 100

            summary.text = """
                ✅ Accuracy: ${"%.2f".format(accuracy)}%
                
                TP: $tp  TN: $tn
                FP: $fp  FN: $fn
                
                Sanity: $sanity
            """.trimIndent()
        }
    }

    // 🧠 BASIC SANITY TEST
    private suspend fun runSanityCheck(): String = withContext(Dispatchers.Default) {
        val tests = listOf(
            "I love this" to "POSITIVE",
            "This is amazing" to "POSITIVE",
            "I hate this" to "NEGATIVE",
            "This is terrible" to "NEGATIVE"
        )

        var correct = 0

        tests.forEach {
            val clean = preprocess(it.first)
            val res = analyzer.analyze(clean)
            val pred = normalize(res.label)

            Log.d("SANITY", "${it.first} -> $pred (${res.score})")

            if (pred == it.second) correct++
        }

        return@withContext "$correct/${tests.size}"
    }

    // 🧹 TEXT CLEANING
    private fun preprocess(text: String): String {
        return text
            .lowercase()
            .trim()
            .take(512)
    }

    // 🔁 LABEL NORMALIZATION
    private fun normalize(label: String): String {
        val l = label.uppercase().trim()

        return when {
            l.contains("POS") || l == "LABEL_1" -> "POSITIVE"
            l.contains("NEG") || l == "LABEL_0" -> "NEGATIVE"
            else -> l
        }
    }

    // 📂 LOAD JSON DATASET
    private fun loadTestData(): List<TestData> {
        return try {
            val input = requireContext().assets.open("sentiment_test_data.json")
            val json = input.bufferedReader().use { it.readText() }

            val type = object : TypeToken<List<TestData>>() {}.type
            Gson().fromJson(json, type)

        } catch (e: IOException) {
            e.printStackTrace()
            emptyList()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        analyzer.close()
    }

    // 📊 ADAPTER
    class TestResultAdapter : RecyclerView.Adapter<TestResultAdapter.VH>() {

        private var items = listOf<TestResult>()

        fun submitList(newItems: List<TestResult>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]

            holder.t1.text = "${if (item.isCorrect) "✅" else "❌"} [${item.actual}] ${item.text}"
            holder.t2.text = "Expected: ${item.expected} | Conf: ${"%.2f".format(item.score)}"

            holder.t1.setTextColor(
                if (item.isCorrect) Color.parseColor("#2E7D32")
                else Color.RED
            )
        }

        override fun getItemCount() = items.size

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val t1: TextView = v.findViewById(android.R.id.text1)
            val t2: TextView = v.findViewById(android.R.id.text2)
        }
    }
}