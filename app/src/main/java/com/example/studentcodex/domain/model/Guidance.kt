package com.example.studentcodex.domain.model

/** A message in the guidance assistant conversation. */
data class GuidanceMessage(
    val id: String,
    val isFromUser: Boolean,
    val text: String,
    /** Optional follow-up suggestions offered with assistant replies. */
    val suggestions: List<String> = emptyList(),
)
