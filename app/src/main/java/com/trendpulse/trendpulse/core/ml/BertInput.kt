package com.trendpulse.trendpulse.core.ml

data class BertInput(
    val inputIds: IntArray,
    val attentionMask: IntArray,
    val tokenTypeIds: IntArray
)