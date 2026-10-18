package com.example.core.intelligence.conversation

import java.util.UUID

enum class ParticipantRole {
  USER,
  ASSISTANT
}

data class ActionableOption(
  val title: String,
  val queryToExecute: String
)

data class ConversationTurn(
  val id: String = UUID.randomUUID().toString(),
  val role: ParticipantRole,
  val text: String,
  val timestamp: Long = System.currentTimeMillis(),
  val topic: String? = null,
  val actionableSuggestions: List<ActionableOption> = emptyList()
)
