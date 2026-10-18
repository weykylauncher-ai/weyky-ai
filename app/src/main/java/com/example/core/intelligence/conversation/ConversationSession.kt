package com.example.core.intelligence.conversation

/**
 * Indicates if the assistant is awaiting specific follow-up information from the user.
 */
enum class PendingExpectation {
  NONE,
  AWAITING_SONG_TITLE,
  AWAITING_CLARIFICATION
}

/**
 * Manages in-memory dialogue session history, tracking user/assistant turns,
 * current focus topic, mentioned entities, pending expectations, and context for multi-turn reasoning.
 */
class ConversationSession(private val maxTurns: Int = 20) {
  private val _turns = mutableListOf<ConversationTurn>()
  val turns: List<ConversationTurn> get() = _turns.toList()

  var currentTopic: String? = null
  var pendingExpectation: PendingExpectation = PendingExpectation.NONE
  var lastReferencedSubject: String? = null
  private val entitiesMentioned = mutableSetOf<String>()

  fun recordUserTurn(text: String): ConversationTurn {
    val turn = ConversationTurn(
      role = ParticipantRole.USER,
      text = text,
      topic = currentTopic
    )
    addTurn(turn)
    detectAndSetTopic(text)
    extractEntities(text)
    return turn
  }

  fun recordAssistantTurn(
    text: String,
    suggestions: List<ActionableOption> = emptyList(),
    newTopic: String? = null,
    expectation: PendingExpectation = PendingExpectation.NONE
  ): ConversationTurn {
    if (newTopic != null) {
      currentTopic = newTopic
    }
    pendingExpectation = expectation
    val turn = ConversationTurn(
      role = ParticipantRole.ASSISTANT,
      text = text,
      topic = currentTopic,
      actionableSuggestions = suggestions
    )
    addTurn(turn)
    return turn
  }

  fun getLastTurn(): ConversationTurn? = _turns.lastOrNull()
  fun getLastUserTurn(): ConversationTurn? = _turns.lastOrNull { it.role == ParticipantRole.USER }
  fun getLastAssistantTurn(): ConversationTurn? = _turns.lastOrNull { it.role == ParticipantRole.ASSISTANT }

  fun getRecentTurns(count: Int = 6): List<ConversationTurn> {
    return _turns.takeLast(count)
  }

  fun clear() {
    _turns.clear()
    currentTopic = null
    pendingExpectation = PendingExpectation.NONE
    lastReferencedSubject = null
    entitiesMentioned.clear()
  }

  private fun addTurn(turn: ConversationTurn) {
    _turns.add(turn)
    if (_turns.size > maxTurns) {
      _turns.removeAt(0)
    }
  }

  private fun detectAndSetTopic(text: String) {
    val lower = text.lowercase()
    when {
      lower.contains("هاتف") || lower.contains("جهاز") || lower.contains("تلفون") || lower.contains("phone") || lower.contains("device") -> {
        currentTopic = "device_info"
        lastReferencedSubject = "phone"
      }
      lower.contains("أغنية") || lower.contains("اغنية") || lower.contains("موسيقى") || lower.contains("موسيق") || lower.contains("song") || lower.contains("music") -> {
        currentTopic = "music"
        lastReferencedSubject = "music"
      }
      lower.contains("بطارية") || lower.contains("شحن") || lower.contains("battery") -> {
        currentTopic = "battery"
        lastReferencedSubject = "battery"
      }
      lower.contains("موعد") || lower.contains("اجتماع") || lower.contains("جدول") || lower.contains("meeting") || lower.contains("schedule") -> {
        currentTopic = "schedule"
        lastReferencedSubject = "schedule"
      }
      lower.contains("طقس") || lower.contains("جو") || lower.contains("مطر") || lower.contains("weather") -> {
        currentTopic = "weather"
        lastReferencedSubject = "weather"
      }
      lower.contains("سفر") || lower.contains("سياحة") || lower.contains("رحلة") || lower.contains("travel") -> {
        currentTopic = "travel"
      }
      lower.contains("صحة") || lower.contains("نوم") || lower.contains("رياضة") || lower.contains("health") || lower.contains("sleep") -> {
        currentTopic = "health_wellness"
      }
      lower.contains("حساب") || lower.contains("رياضيات") || lower.contains("معادلة") || lower.contains("math") -> {
        currentTopic = "calculation"
      }
    }
  }

  private fun extractEntities(text: String) {
    val keywords = listOf(
      "صلالة", "مسقط", "الرياض", "دبي", "القاهرة", "مكة", "المدينة", "باريس", "لندن", "طوكيو",
      "الذكاء الاصطناعي", "الفيزياء", "الفضاء", "التاريخ", "الفلسفة", "البرمجة",
      "البطارية", "الشبكة", "الصوت", "الإشعارات"
    )
    for (k in keywords) {
      if (text.contains(k, ignoreCase = true)) {
        entitiesMentioned.add(k)
      }
    }
  }

  fun getMentionedEntities(): Set<String> = entitiesMentioned.toSet()
}
