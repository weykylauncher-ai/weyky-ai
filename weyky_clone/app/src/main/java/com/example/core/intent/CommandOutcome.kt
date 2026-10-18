package com.example.core.intent

sealed interface SpeechResult {
  object Success : SpeechResult
  data class Failed(val reason: String) : SpeechResult
  object Skipped : SpeechResult
}

/**
 * Encapsulates the overall result of a user voice/text interaction.
 * Core architectural invariant:
 * The execution of the command (Android/Device operation) is decoupled from the TTS speech result.
 * If the execution succeeds (e.g. opening an app, changing volume, skipping a track),
 * the command outcome is SUCCESS, even if TextToSpeech fails afterwards.
 */
data class CommandOutcome(
  val commandId: String,
  val execution: ExecutionResult,
  val speech: SpeechResult
) {
  val isSuccessful: Boolean
    get() = execution is ExecutionResult.Success
}
