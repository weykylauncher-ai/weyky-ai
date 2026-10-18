package com.example.core.intent

import com.example.core.intelligence.conversation.ConversationSession
import com.example.core.intelligence.conversation.PendingExpectation
import java.util.Locale

enum class MessageIntentType {
  ACTION_EXECUTION,         // "شغل الموسيقى", "افتح واتساب"
  HYPOTHETICAL_QUESTION,    // "لو شغلت الموسيقى الآن هل سيؤثر على البطارية؟"
  INFORMATIONAL_QUESTION,   // "ما هو هاتفي؟", "من انا؟"
  FOLLOW_UP_QUERY,          // "وماذا تعرف عنه؟", أو إرسال اسم الأغنية بعد سؤال المساعد
  HYBRID_REQUEST,           // طلب مركب
  GENERAL_CONVERSATION      // دردشة، علوم، نصائح
}

data class ClassifiedMessage(
  val type: MessageIntentType,
  val primarySubject: String? = null,
  val isHypothetical: Boolean = false,
  val extractedDelayMinutes: Int? = null,
  val extractedSongTitle: String? = null,
  val confidence: Float = 1.0f
)

object MessageIntentClassifier {

  fun normalize(raw: String): String {
    return raw.trim()
      .lowercase(Locale.ROOT)
      .replace(Regex("[\\u064B-\\u065F\\u0670]"), "") // Strip all tashkeel & shaddah
      .replace('أ', 'ا')
      .replace('إ', 'ا')
      .replace('آ', 'ا')
      .replace('ة', 'ه')
      .replace('ى', 'ي')
      .replace('ؤ', 'و')
      .replace('ئ', 'ي')
      .replace(Regex("[\\?\\!\\.\\,\\؟\\:]"), " ")
      .replace(Regex("\\s+"), " ")
      .trim()
  }

  fun classify(rawInput: String, session: ConversationSession?): ClassifiedMessage {
    val norm = normalize(rawInput)

    // 1. Check if assistant is currently awaiting song title from a previous turn
    if (session?.pendingExpectation == PendingExpectation.AWAITING_SONG_TITLE) {
      val isCancellation = norm.contains("الغاء") || norm.contains("لا شكرا") || norm.contains("توقف") || norm.contains("انسى") || norm.contains("cancel")
      if (!isCancellation && rawInput.isNotBlank()) {
        val cleanedTitle = extractSongTitleOnly(rawInput)
        return ClassifiedMessage(
          type = MessageIntentType.FOLLOW_UP_QUERY,
          primarySubject = "song_title_provided",
          extractedSongTitle = cleanedTitle
        )
      }
    }

    // 2. Hypothetical Questions: "لو...", "ماذا لو...", "هل سيؤثر لو...", "if I..."
    val isHypothetical = norm.contains("لو ") || norm.startsWith("لو") ||
      norm.contains("ماذا لو") || norm.contains("اذا شغلت") || norm.contains("اذا تاخرت") ||
      norm.contains("هل سياثر") || norm.contains("هل سيؤثر") || norm.contains("هل يؤثر") ||
      norm.contains("هل بتصرف") || norm.contains("هل تستهلك") || norm.contains("كم تستهلك") ||
      norm.contains("هل ينقص") || norm.contains("تستهلك بطاريه") ||
      norm.contains("what if") || norm.contains("what happens if") || norm.contains("what would happen if") ||
      norm.contains("if i play") || norm.contains("if i delay") || norm.contains("if i turn on")

    if (isHypothetical) {
      when {
        norm.contains("موسيقي") || norm.contains("موسيقى") || norm.contains("اغنيه") || norm.contains("music") || norm.contains("song") -> {
          return ClassifiedMessage(
            type = MessageIntentType.HYPOTHETICAL_QUESTION,
            primarySubject = "battery_music_impact",
            isHypothetical = true
          )
        }
        norm.contains("تاخرت") || norm.contains("تأخرت") || norm.contains("delay") || norm.contains("late") -> {
          val delayMins = extractMinutes(norm) ?: 10
          return ClassifiedMessage(
            type = MessageIntentType.HYPOTHETICAL_QUESTION,
            primarySubject = "schedule_delay_impact",
            isHypothetical = true,
            extractedDelayMinutes = delayMins
          )
        }
        norm.contains("كشاف") || norm.contains("فلاش") || norm.contains("مصباح") || norm.contains("torch") || norm.contains("flashlight") -> {
          return ClassifiedMessage(
            type = MessageIntentType.HYPOTHETICAL_QUESTION,
            primarySubject = "battery_flashlight_impact",
            isHypothetical = true
          )
        }
        else -> {
          return ClassifiedMessage(
            type = MessageIntentType.HYPOTHETICAL_QUESTION,
            primarySubject = "general_hypothetical",
            isHypothetical = true
          )
        }
      }
    }

    // 3. Follow-up to Previous Turns
    val isPronounFollowUp = norm == "وماذا تعرف عنه" || norm == "ماذا تعرف عنه" ||
      norm == "حدثني عنه" || norm == "تفاصيل اكثر" || norm == "مواصفاته" || norm == "معلومات عنه" ||
      norm == "وماذا عنه" || norm.startsWith("وماذا تعرف عن") || norm.startsWith("اخبرني عنه") ||
      norm == "and what do you know about it" || norm == "what about it"

    if (isPronounFollowUp) {
      if (session?.currentTopic == "device_info" || session?.lastReferencedSubject == "phone") {
        return ClassifiedMessage(
          type = MessageIntentType.FOLLOW_UP_QUERY,
          primarySubject = "device_detailed_specs"
        )
      }
    }

    // 4. User Identity: "من انا؟"
    if (norm == "من انا" || norm == "من اكون" || norm == "مين انا" ||
      norm == "هل تعرفني" || norm == "ما اسمي" || norm == "شنو اسمي" ||
      norm == "who am i" || norm == "do you know me" || norm == "what is my name"
    ) {
      return ClassifiedMessage(
        type = MessageIntentType.INFORMATIONAL_QUESTION,
        primarySubject = "user_identity"
      )
    }

    // 5. Phone Model & Detailed Specs
    if (norm.contains("ما هو هاتفي") || norm.contains("ما هاتفي") || norm.contains("نوع هاتفي") || norm.contains("موديل هاتفي") ||
      norm.contains("اسم هاتفي") || norm.contains("طراز هاتفي") || norm.contains("ما هو جهازي") || norm.contains("نوع جهازي") ||
      norm.contains("شنو جهازي") || norm.contains("what phone is this") || norm.contains("what is my phone") || norm.contains("phone model")
    ) {
      return ClassifiedMessage(
        type = MessageIntentType.INFORMATIONAL_QUESTION,
        primarySubject = "phone_model"
      )
    }

    if (norm.contains("ماذا تعرف عن هاتفي") || norm.contains("ماذا تعرف عن جهازي") ||
      norm.contains("مواصفات هاتفي") || norm.contains("معلومات هاتفي") || norm.contains("مواصفات الجهاز") ||
      norm.contains("what do you know about my phone") || norm.contains("device specs") || norm.contains("phone specs")
    ) {
      return ClassifiedMessage(
        type = MessageIntentType.INFORMATIONAL_QUESTION,
        primarySubject = "device_detailed_specs"
      )
    }

    // 6. Specific Song Queries & Playback
    if (norm.contains("تشغيل اغنيه معينه") || norm.contains("تشغيل اغنيه محدده") ||
      norm.contains("شغل اغنيه معينه") || norm.contains("اريد تشغيل اغنيه") ||
      norm == "شغل اغنيه" || norm == "play a song" || norm == "play specific song"
    ) {
      return ClassifiedMessage(
        type = MessageIntentType.INFORMATIONAL_QUESTION,
        primarySubject = "request_song_title"
      )
    }

    val hasExplicitSongTitlePrefix = norm.startsWith("شغل اغنيه ") || norm.startsWith("تشغيل اغنيه ") ||
      norm.startsWith("play song ") || norm.startsWith("play the song ")
    if (hasExplicitSongTitlePrefix) {
      val title = extractSongTitleOnly(rawInput)
      if (title.isNotBlank()) {
        return ClassifiedMessage(
          type = MessageIntentType.ACTION_EXECUTION,
          primarySubject = "play_specific_song",
          extractedSongTitle = title
        )
      }
    }

    // 7. General Media Playback Direct Commands
    val isMediaPlayCommand = (norm.contains("شغل الموسيقي") || norm.contains("تشغيل الموسيقي") ||
      norm.contains("شغل موسيقي") || norm.contains("استمر بتشغيل الموسيقي") ||
      norm == "شغل" || norm.contains("استئناف الموسيقي") ||
      norm == "play music" || norm == "play" || norm == "resume music") &&
      !isHypothetical

    if (isMediaPlayCommand) {
      return ClassifiedMessage(
        type = MessageIntentType.ACTION_EXECUTION,
        primarySubject = "media_play"
      )
    }

    // Default classification
    return ClassifiedMessage(
      type = MessageIntentType.INFORMATIONAL_QUESTION,
      primarySubject = "general"
    )
  }

  fun extractSongTitleOnly(rawInput: String): String {
    val norm = normalize(rawInput)
    val normalizedPrefixes = listOf(
      "شغل اغنيه ", "تشغيل اغنيه ", "شغل لي اغنيه ", "ابحث عن اغنيه ", "اغنيه ",
      "اسم الاغنيه ", "play song ", "play the song ", "song "
    )
    for (p in normalizedPrefixes) {
      if (norm.startsWith(p)) {
        val rawTokens = rawInput.trim().split(Regex("\\s+"))
        val prefixCount = p.trim().split(Regex("\\s+")).size
        if (rawTokens.size > prefixCount) {
          return rawTokens.drop(prefixCount).joinToString(" ").replace(Regex("[\\?\\!\\.\\,\\؟\\:]"), "").trim()
        }
        return norm.substring(p.length).trim()
      }
    }
    return rawInput.replace(Regex("[\\?\\!\\.\\,\\؟\\:]"), "").trim()
  }

  private fun extractMinutes(norm: String): Int? {
    val converted = norm.map { char ->
      when (char) {
        '٠' -> '0'; '١' -> '1'; '٢' -> '2'; '٣' -> '3'; '٤' -> '4'
        '٥' -> '5'; '٦' -> '6'; '٧' -> '7'; '٨' -> '8'; '٩' -> '9'
        else -> char
      }
    }.joinToString("")
    val regex = Regex("""(\d{1,3})""")
    val match = regex.find(converted)
    if (match != null) {
      val num = match.groupValues[1].toIntOrNull()
      if (num != null && num in 1..180) {
        return num
      }
    }
    return when {
      converted.contains("عشر دقائق") || converted.contains("10 دقائق") || converted.contains("عشره دقائق") || converted.contains("10 min") -> 10
      converted.contains("خمس دقائق") || converted.contains("5 دقائق") || converted.contains("خمسه دقائق") -> 5
      converted.contains("ربع ساعه") || converted.contains("15 دقيقه") -> 15
      converted.contains("نصف ساعه") || converted.contains("30 دقيقه") -> 30
      converted.contains("ساعه") || converted.contains("60 دقيقه") -> 60
      else -> null
    }
  }
}
