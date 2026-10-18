package com.example.core.intent.handlers

import com.example.core.intelligence.conversation.ActionableOption
import com.example.core.intent.ExecutionResult
import com.example.core.intent.WeykyIntent
import com.example.device.media.MediaControlResult
import com.example.device.media.MediaPlaybackController
import com.example.rendering.visual_state.CoreVisualState
import java.util.Locale

class MediaActionHandler(private val mediaController: MediaPlaybackController) {

  fun handle(action: WeykyIntent.MediaAction): ExecutionResult {
    val isArabic = Locale.getDefault().language.startsWith("ar")

    val result = when (action) {
      WeykyIntent.MediaAction.PLAY -> mediaController.play()
      WeykyIntent.MediaAction.PAUSE -> mediaController.pause()
      WeykyIntent.MediaAction.TOGGLE -> mediaController.toggle()
      WeykyIntent.MediaAction.NEXT -> mediaController.skipToNext()
      WeykyIntent.MediaAction.PREVIOUS -> mediaController.skipToPrevious()
    }

    return when (result) {
      is MediaControlResult.Success -> {
        ExecutionResult.Success(
          speech = result.message,
          actionTaken = result.message,
          visualState = CoreVisualState.MUSIC
        )
      }
      is MediaControlResult.NoActiveSession -> {
        val message = if (isArabic) {
          "لا يوجد مشغل موسيقى نشط حالياً. افتح تطبيق الصوتيات أولاً."
        } else {
          "No active media session is currently playing."
        }
        ExecutionResult.Unavailable(
          reason = message,
          visualState = CoreVisualState.RESPONDING
        )
      }
      is MediaControlResult.Error -> {
        ExecutionResult.Error(
          friendlyMessage = result.message,
          visualState = CoreVisualState.ERROR
        )
      }
    }
  }

  fun handlePlaySpecificSong(songTitle: String): ExecutionResult {
    val isArabic = songTitle.any { it in '\u0600'..'\u06FF' } || Locale.getDefault().language.startsWith("ar")
    val cleaned = songTitle.trim()

    // Check if the currently active media session happens to match the requested title
    val currentMeta = mediaController.mediaState.value
    if (currentMeta.title?.contains(cleaned, ignoreCase = true) == true) {
      val playRes = mediaController.play()
      val msg = if (isArabic) "جاري تشغيل '$cleaned' من الجلسة الصوتية الحالية." else "Playing '$cleaned' from active media session."
      return ExecutionResult.Success(
        speech = msg,
        actionTaken = "Playing matching song '$cleaned'",
        visualState = CoreVisualState.MUSIC
      )
    }

    // Honest handling: Never pretend or unpause random audio!
    val explanation = if (isArabic) {
      "بحثت عن أغنية '$cleaned'، ولكن لم يتم العثور على مقطع صوتي محلي بهذا الاسم في المشغل الحالي. يمكنك فتح يوتيوب أو تطبيق الموسيقى المفضل لديك للاستماع إليها."
    } else {
      "I searched for '$cleaned', but no local audio track with this title was found on your device. You can open YouTube or Spotify to listen to it."
    }

    return ExecutionResult.Success(
      speech = explanation,
      actionTaken = "Specific song '$cleaned' not found locally",
      visualState = CoreVisualState.RESPONDING,
      proactiveSuggestions = listOf(
        ActionableOption(if (isArabic) "فتح يوتيوب" else "Search on YouTube", "افتح يوتيوب"),
        ActionableOption(if (isArabic) "فتح سبوتيفاي" else "Search on Spotify", "افتح سبوتيفاي"),
        ActionableOption(if (isArabic) "تشغيل الصوت المتاح" else "Play available audio", "شغل الموسيقى")
      )
    )
  }

  fun handleRequestSongTitle(): ExecutionResult {
    val isArabic = Locale.getDefault().language.startsWith("ar")
    val prompt = if (isArabic) {
      "بكل سرور، ما هو اسم الأغنية التي ترغب في تشغيلها؟"
    } else {
      "What is the name of the song you'd like to play?"
    }
    return ExecutionResult.Success(
      speech = prompt,
      actionTaken = "Requested song title",
      visualState = CoreVisualState.LISTENING,
      proactiveSuggestions = listOf(
        ActionableOption(if (isArabic) "شغّل الموسيقى الحالية" else "Play music now", "شغل الموسيقى")
      )
    )
  }
}
