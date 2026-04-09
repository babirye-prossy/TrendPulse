package com.trendpulse.trendpulse.core.ml

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

class WordPieceTokenizer(
    context: Context,
    vocabAssetName: String = "vocab.txt",
    private val maxSeqLen: Int = 128,
    private val doLowerCase: Boolean = true
) {

    private val vocab: Map<String, Int>
    private val clsId: Int
    private val sepId: Int
    private val padId: Int
    private val unkId: Int

    init {
        val vocabList = mutableListOf<String>()
        context.assets.open(vocabAssetName).use { input ->
            BufferedReader(InputStreamReader(input)).useLines { lines ->
                lines.forEach { vocabList.add(it.trim()) }
            }
        }

        vocab = vocabList.withIndex().associate { it.value to it.index }

        clsId = vocab["[CLS]"] ?: error("[CLS] not found in vocab")
        sepId = vocab["[SEP]"] ?: error("[SEP] not found in vocab")
        padId = vocab["[PAD]"] ?: error("[PAD] not found in vocab")
        unkId = vocab["[UNK]"] ?: error("[UNK] not found in vocab")
    }

    fun encode(text: String): BertInput {
        val basicTokens = basicTokenize(text)
        val wordPieceTokens = mutableListOf<String>()

        for (token in basicTokens) {
            wordPieceTokens.addAll(wordPieceTokenize(token))
        }

        // Reserve 2 spots for [CLS] and [SEP]
        val maxTokens = maxSeqLen - 2
        val trimmed = if (wordPieceTokens.size > maxTokens) {
            wordPieceTokens.take(maxTokens)
        } else {
            wordPieceTokens
        }

        val inputIds = IntArray(maxSeqLen) { padId }
        val attentionMask = IntArray(maxSeqLen) { 0 }
        val tokenTypeIds = IntArray(maxSeqLen) { 0 }

        var pos = 0
        inputIds[pos] = clsId
        attentionMask[pos] = 1
        pos++

        for (token in trimmed) {
            inputIds[pos] = vocab[token] ?: unkId
            attentionMask[pos] = 1
            pos++
        }

        inputIds[pos] = sepId
        attentionMask[pos] = 1

        return BertInput(
            inputIds = inputIds,
            attentionMask = attentionMask,
            tokenTypeIds = tokenTypeIds
        )
    }

    private fun basicTokenize(text: String): List<String> {
        val normalized = if (doLowerCase) text.lowercase(Locale.US) else text

        val tokens = mutableListOf<String>()
        val current = StringBuilder()

        fun flush() {
            if (current.isNotEmpty()) {
                tokens.add(current.toString())
                current.clear()
            }
        }

        for (ch in normalized) {
            when {
                ch.isWhitespace() -> flush()
                ch.isLetterOrDigit() -> current.append(ch)
                else -> {
                    flush()
                    tokens.add(ch.toString())
                }
            }
        }
        flush()

        return tokens.filter { it.isNotBlank() }
    }

    private fun wordPieceTokenize(token: String): List<String> {
        if (vocab.containsKey(token)) return listOf(token)

        if (token.length > 100) return listOf("[UNK]")

        val subTokens = mutableListOf<String>()
        var start = 0
        var isBad = false

        while (start < token.length) {
            var end = token.length
            var currentSubstr: String? = null

            while (start < end) {
                var substr = token.substring(start, end)
                if (start > 0) substr = "##$substr"

                if (vocab.containsKey(substr)) {
                    currentSubstr = substr
                    break
                }
                end--
            }

            if (currentSubstr == null) {
                isBad = true
                break
            }

            subTokens.add(currentSubstr)
            start = end
        }

        return if (isBad) listOf("[UNK]") else subTokens
    }
}