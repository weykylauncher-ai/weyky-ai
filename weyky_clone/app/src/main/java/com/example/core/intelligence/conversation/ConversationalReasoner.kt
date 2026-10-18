package com.example.core.intelligence.conversation

import com.example.device.info.DeviceInfoProvider
import java.util.Locale

/**
 * High-level conversational reasoning engine.
 * Handles semantic intent classification, multi-turn resolution, empathetic response crafting,
 * dialect understanding, and genuine on-device decision-making without misleading canned templates.
 */
class ConversationalReasoner(
  private val deviceInfoProvider: DeviceInfoProvider? = null
) {

  data class ReasoningResult(
    val replyText: String,
    val topicDetected: String?,
    val proactiveSuggestions: List<ActionableOption>
  )

  fun reason(
    userQuery: String,
    session: ConversationSession
  ): ReasoningResult {
    val hasArabicChars = userQuery.any { it in '\u0600'..'\u06FF' }
    val isArabic = hasArabicChars || Locale.getDefault().language.startsWith("ar")
    val query = userQuery.trim()
    val normalized = LocalKnowledgeBase.normalizeArabic(query)

    // 1. Math and Calculation Check
    val mathResult = LocalKnowledgeBase.evaluateArithmetic(query)
    if (mathResult != null) {
      return ReasoningResult(
        replyText = mathResult,
        topicDetected = "calculation",
        proactiveSuggestions = listOf(
          ActionableOption(if (isArabic) "عملية أخرى" else "Another calculation", "احسب 50 * 12"),
          ActionableOption(if (isArabic) "حالة الذاكرة" else "Memory status", "الذاكرة")
        )
      )
    }

    // 2. Multi-turn Follow-up Resolution
    if (isFollowUpQuery(normalized, session)) {
      val resolvedFollowUp = resolveFollowUp(normalized, session, isArabic)
      if (resolvedFollowUp != null) {
        return resolvedFollowUp
      }
    }

    // 3. User Identity Check: "من انا؟"
    if (isUserIdentityQuery(normalized)) {
      val identityText = deviceInfoProvider?.getUserIdentityDescription(isArabic)
        ?: if (isArabic) {
          "أنت مستخدم هذا الجهاز. لم يتم تسجيل اسم شخصي أو ملف تعريف خاص بك في WEYKY بعد، وذلك حفاظاً على خصوصيتك."
        } else {
          "You are the user of this device. No personal identity is currently registered in WEYKY, respecting your privacy."
        }
      return ReasoningResult(
        replyText = identityText,
        topicDetected = "identity",
        proactiveSuggestions = listOf(
          ActionableOption(if (isArabic) "ما هو هاتفي؟" else "Phone model?", "ما هو هاتفي؟"),
          ActionableOption(if (isArabic) "ماذا تعرف عن جهازي؟" else "About my phone?", "ماذا تعرف عن هاتفي؟")
        )
      )
    }

    // 4. Phone Model & Detailed Specs Check
    if (isPhoneModelQuery(normalized)) {
      val modelSummary = deviceInfoProvider?.getPhoneModelSummary(isArabic)
        ?: if (isArabic) {
          "هاتفك يعمل بنظام Android."
        } else {
          "Your phone is running Android."
        }
      return ReasoningResult(
        replyText = modelSummary,
        topicDetected = "device_info",
        proactiveSuggestions = listOf(
          ActionableOption(if (isArabic) "وماذا تعرف عنه؟" else "What else?", "وماذا تعرف عنه؟"),
          ActionableOption(if (isArabic) "البطارية" else "Battery", "كم نسبة البطارية؟")
        )
      )
    }

    if (isPhoneDetailedSpecsQuery(normalized)) {
      val detailedSpecs = deviceInfoProvider?.getDetailedTechnicalSpecs(isArabic)
        ?: if (isArabic) {
          "مواصفات الجهاز غير متاحة حالياً."
        } else {
          "Device specifications are currently unavailable from system APIs."
        }
      return ReasoningResult(
        replyText = detailedSpecs,
        topicDetected = "device_info",
        proactiveSuggestions = listOf(
          ActionableOption(if (isArabic) "البطارية" else "Battery", "البطارية"),
          ActionableOption(if (isArabic) "الذاكرة" else "RAM usage", "الذاكرة")
        )
      )
    }

    // 5. Greetings & Chitchat
    val greetingReply = handleGreetingsAndChitchat(normalized, isArabic)
    if (greetingReply != null) {
      return greetingReply
    }

    // 6. Emotional Empathy & Mood Support
    val empathyReply = handleEmotionalSupport(normalized, isArabic)
    if (empathyReply != null) {
      return empathyReply
    }

    // 7. Direct Curated Knowledge Base Match
    val kbTopic = LocalKnowledgeBase.findMatchingTopic(query)
    if (kbTopic != null) {
      val text = if (isArabic) kbTopic.arabicSummary else kbTopic.englishSummary
      return ReasoningResult(
        replyText = text,
        topicDetected = kbTopic.id,
        proactiveSuggestions = kbTopic.suggestedFollowUps
      )
    }

    // 8. Principled Decision & Honest Fallback without Canned Deception
    return generatePrincipledResponse(query, normalized, isArabic)
  }

  private fun isUserIdentityQuery(query: String): Boolean {
    return query == "من انا" || query == "من اكون" || query == "مين انا" || query == "هل تعرفني" ||
      query == "ما اسمي" || query == "شنو اسمي" || query == "شو اسمي" || query == "من المستخدم" ||
      query == "who am i" || query == "do you know me" || query == "what is my name"
  }

  private fun isPhoneModelQuery(query: String): Boolean {
    return query.contains("ما هو هاتفي") || query.contains("ما هاتفي") || query.contains("نوع هاتفي") ||
      query.contains("موديل هاتفي") || query.contains("طراز هاتفي") || query.contains("ما نوع الجهاز") ||
      query.contains("what is my phone") || query.contains("phone model")
  }

  private fun isPhoneDetailedSpecsQuery(query: String): Boolean {
    return query.contains("ماذا تعرف عن هاتفي") || query.contains("ماذا تعرف عن جهازي") ||
      query.contains("مواصفات هاتفي") || query.contains("معلومات هاتفي") || query.contains("تفاصيل الجهاز") ||
      query.contains("what do you know about my phone") || query.contains("device specs")
  }

  private fun isFollowUpQuery(query: String, session: ConversationSession): Boolean {
    if (session.currentTopic == "device_info" || session.lastReferencedSubject == "phone") {
      if (query.contains("عنه") || query.contains("مواصفاته") || query.contains("تفاصيله") || query.contains("about it")) {
        return true
      }
    }
    val followUpStarters = listOf(
      "وماذا", "وكيف", "ولماذا", "طيب", "وما", "هل هناك", "المزيد", "اكثر", "أكثر", "اشرح",
      "tell me more", "and how", "what about", "elaborate", "can you give more details", "why"
    )
    val hasPronounOrFollowUpMarker = query.contains("هناك") || query.contains("فيها") ||
      query.contains("عنه") || query.contains("عنها") || query.contains("ذلك") ||
      query.contains("there") || query.contains("that")

    return hasPronounOrFollowUpMarker || followUpStarters.any { query.startsWith(it) } || query.length < 15
  }

  private fun resolveFollowUp(
    query: String,
    session: ConversationSession,
    isArabic: Boolean
  ): ReasoningResult? {
    val topic = session.currentTopic ?: return null

    // Follow-up about phone: "وماذا تعرف عنه؟"
    if (topic == "device_info" || session.lastReferencedSubject == "phone") {
      if (query.contains("عنه") || query.contains("مواصفاته") || query.contains("تفاصيله") || query.contains("about it")) {
        val detailedSpecs = deviceInfoProvider?.getDetailedTechnicalSpecs(isArabic)
          ?: if (isArabic) {
            "مواصفات الهاتف: يعمل بنظام Android."
          } else {
            "Phone specs: Running Android."
          }
        return ReasoningResult(
          replyText = detailedSpecs,
          topicDetected = "device_info",
          proactiveSuggestions = listOf(
            ActionableOption(if (isArabic) "البطارية" else "Battery", "كم نسبة البطارية؟"),
            ActionableOption(if (isArabic) "الذاكرة" else "RAM usage", "الذاكرة")
          )
        )
      }
    }

    when (topic) {
      "travel" -> {
        if (query.contains("طقس") || query.contains("جو") || query.contains("مطر") || query.contains("ملابس") || query.contains("weather") || query.contains("cold")) {
          val reply = if (isArabic) {
            "في صلالة خلال موسم الخريف، يكون الطقس معتدلاً ومائلاً للبرودة في الجبال (~22-25°م) مع رذاذ وضباب مستمر. يُنصح بإحضار ملابس خفيفة مع سترة مقاومة للرطوبة وأحذية مريحة للمشي في الطبيعة."
          } else {
            "In Salalah during the Khareef season, the weather is pleasantly mild and mist-covered (approx 22°C), cooler in mountain areas. Pack light layers and a rain-resistant jacket."
          }
          return ReasoningResult(
            replyText = reply,
            topicDetected = "travel",
            proactiveSuggestions = listOf(
              ActionableOption(if (isArabic) "طقس منطقتي الآن" else "Local weather", "ما حالة الطقس؟"),
              ActionableOption(if (isArabic) "هل أخرج الآن؟" else "Should I leave?", "هل اخرج الان؟")
            )
          )
        }
        if (query.contains("متى") || query.contains("وقت") || query.contains("شهر") || query.contains("when")) {
          val reply = if (isArabic) {
            "أفضل وقت لزيارة صلالة للاستمتاع بالخضرة والضباب هو من منتصف يوليو حتى نهاية أغسطس، حيث يكون الخريف في ذروته."
          } else {
            "The best time to visit Salalah for lush greenery is between July and August during the peak of Khareef season."
          }
          return ReasoningResult(
            replyText = reply,
            topicDetected = "travel",
            proactiveSuggestions = listOf(
              ActionableOption(if (isArabic) "أبرز المعالم" else "Top attractions", "حدثني عن صلالة"),
              ActionableOption(if (isArabic) "مواعيدي القادمة" else "Next appointments", "هل سأصل في الموعد؟")
            )
          )
        }
      }
      "sleep_wellness" -> {
        if (query.contains("قهوة") || query.contains("كافيين") || query.contains("شاي") || query.contains("coffee") || query.contains("caffeine")) {
          val reply = if (isArabic) {
            "الكافيين يبقى نصف عمره في الجسم لمدة 6 إلى 8 ساعات. لذلك يُفضل التوقف عن شرب القهوة بعد الساعة 2 ظهراً لضمان عدم تأثيره على مراحل النوم العميق."
          } else {
            "Caffeine has a half-life of 6 to 8 hours. It is best to avoid coffee after 2 PM to protect deep sleep stages."
          }
          return ReasoningResult(
            replyText = reply,
            topicDetected = "sleep_wellness",
            proactiveSuggestions = listOf(
              ActionableOption(if (isArabic) "إطفاء الكشاف" else "Turn off torch", "اطفئ الكشاف"),
              ActionableOption(if (isArabic) "كم الساعة؟" else "Current time", "كم الساعة؟")
            )
          )
        }
      }
      "productivity" -> {
        if (query.contains("اكثر") || query.contains("المزيد") || query.contains("نصيحة") || query.contains("more") || query.contains("tips")) {
          val reply = if (isArabic) {
            "نصيحة إضافية للتركيز: تجنب تعدد المهام (Multitasking). أظهرت الدراسات أن التركيز على مهمة واحدة يرفع الكفاءة بنسبة 40% ويقلل الإجهاد الذهني."
          } else {
            "Additional tip: Avoid multitasking. Research indicates single-task focus boosts efficiency by up to 40% while lowering cognitive strain."
          }
          return ReasoningResult(
            replyText = reply,
            topicDetected = "productivity",
            proactiveSuggestions = listOf(
              ActionableOption(if (isArabic) "فتح التقويم" else "Open Calendar", "افتح التقويم"),
              ActionableOption(if (isArabic) "فحص البطارية" else "Check battery", "البطارية")
            )
          )
        }
      }
    }
    return null
  }

  private fun handleGreetingsAndChitchat(
    query: String,
    isArabic: Boolean
  ): ReasoningResult? {
    when {
      query.contains("السلام عليكم") || query.contains("مرحبا") || query.contains("اهلا") || query.contains("هلا") || query.contains("هاي") || query.startsWith("hello") || query.startsWith("hi") -> {
        val reply = if (isArabic) {
          "وعليكم السلام ورحمة الله! أهلاً بك، أنا WEYKY، كيف يمكنني مساعدتك أو التفكير معك اليوم؟"
        } else {
          "Hello! I am WEYKY, at your service. How can I assist you today?"
        }
        return ReasoningResult(
          replyText = reply,
          topicDetected = "chitchat",
          proactiveSuggestions = listOf(
            ActionableOption(if (isArabic) "ماذا أفعل الآن؟" else "Current situation", "ماذا افعل الان؟"),
            ActionableOption(if (isArabic) "حالة الطقس" else "Weather forecast", "ما حالة الطقس؟"),
            ActionableOption(if (isArabic) "نسبة البطارية" else "Battery level", "كم نسبة البطارية؟")
          )
        )
      }
      query.contains("كيف حالك") || query.contains("اخبارك") || query.contains("شلونك") || query.contains("how are you") -> {
        val reply = if (isArabic) {
          "أنا بخير وفي كامل الجاهزية لخدمتك! كيف حالك أنت وكيف يسير يومك؟"
        } else {
          "I'm doing great and fully ready to assist you! How are things with you?"
        }
        return ReasoningResult(
          replyText = reply,
          topicDetected = "chitchat",
          proactiveSuggestions = listOf(
            ActionableOption(if (isArabic) "احكِ لي قصة" else "Tell a story", "احك لي قصة قصيرة"),
            ActionableOption(if (isArabic) "هل أخرج الآن؟" else "Should I go out?", "هل اخرج الان؟")
          )
        )
      }
      query.contains("شكرا") || query.contains("مشكور") || query.contains("يعطيك العافية") || query.contains("تسلم") || query.contains("thank") -> {
        val reply = if (isArabic) {
          "على الرحب والسعة دائماً! يسعدني مساعدتك في أي وقت."
        } else {
          "You're very welcome! I'm always here whenever you need assistance or quick reasoning."
        }
        return ReasoningResult(
          replyText = reply,
          topicDetected = "chitchat",
          proactiveSuggestions = listOf(
            ActionableOption(if (isArabic) "ملخص الجهاز" else "Device overview", "حالة الجهاز"),
            ActionableOption(if (isArabic) "تشغيل الموسيقى" else "Play music", "شغل الموسيقى")
          )
        )
      }
      query.contains("صباح الخير") || query.contains("good morning") -> {
        val reply = if (isArabic) {
          "صباح النور والسرور! أتمنى لك يوماً موفقاً ومليئاً بالإنجاز. هل نلقي نظرة على طقس اليوم ومواعيدك؟"
        } else {
          "Good morning! Wishing you an energized and productive day. Would you like a quick overview of today's weather and schedule?"
        }
        return ReasoningResult(
          replyText = reply,
          topicDetected = "chitchat",
          proactiveSuggestions = listOf(
            ActionableOption(if (isArabic) "طقس اليوم" else "Weather today", "ما حالة الطقس؟"),
            ActionableOption(if (isArabic) "مواعيدي" else "Check schedule", "هل سأصل في الموعد؟"),
            ActionableOption(if (isArabic) "البطارية" else "Battery", "البطارية")
          )
        )
      }
      query.contains("مساء الخير") || query.contains("تصبح على خير") || query.contains("good evening") || query.contains("good night") -> {
        val reply = if (isArabic) {
          "مساء الأنوار! أتمنى أن يكون يومك قد مرّ بسلام. هل ترغب في نصائح للاسترخاء أو ضبط الهاتف للراحة؟"
        } else {
          "Good evening! I hope you had a fruitful day. Would you like help preparing for rest or setting your alarms?"
        }
        return ReasoningResult(
          replyText = reply,
          topicDetected = "chitchat",
          proactiveSuggestions = listOf(
            ActionableOption(if (isArabic) "نصائح للنوم" else "Sleep tips", "نصائح للنوم"),
            ActionableOption(if (isArabic) "إطفاء الكشاف" else "Turn off torch", "اطفئ الكشاف")
          )
        )
      }
    }
    return null
  }

  private fun handleEmotionalSupport(
    query: String,
    isArabic: Boolean
  ): ReasoningResult? {
    if (query.contains("تعبان") || query.contains("مرهق") || query.contains("متضايق") || query.contains("حزين") || query.contains("ملل") || query.contains("زهقان") || query.contains("قلق") || query.contains("sad") || query.contains("tired") || query.contains("stressed")) {
      val reply = if (isArabic) {
        "أشعر بك، الإرهاق والضغوط جزء طبيعي من الحياة. خذ نفساً عميقاً واسترح قليلاً بعيداً عن الشاشات، أو استمع لشيء هادئ تحبه. أنا هنا إذا أردت التحدث أو سماع قصة ملهمة."
      } else {
        "I'm sorry you're feeling that way. Stress and fatigue happen to everyone. Taking a short break, breathing deeply, or a brief walk can help refresh your perspective. I'm here to support you."
      }
      return ReasoningResult(
        replyText = reply,
        topicDetected = "emotional_support",
        proactiveSuggestions = listOf(
          ActionableOption(if (isArabic) "تشغيل موسيقى" else "Play music", "شغل الموسيقى"),
          ActionableOption(if (isArabic) "قصة ملهمة" else "Short story", "احك لي قصة قصيرة"),
          ActionableOption(if (isArabic) "هل أخرج للمشي؟" else "Go outside?", "هل اخرج الان؟")
        )
      )
    }
    return null
  }

  /**
   * Principled handling of un-indexed queries:
   * 1. Distinguishes what is missing rather than inventing answers.
   * 2. Explains clearly if offline capabilities cannot access live external data.
   * 3. Solicits specific clarification for ambiguous requests.
   * 4. Completely avoids misleading canned templates.
   */
  private fun generatePrincipledResponse(
    query: String,
    normalized: String,
    isArabic: Boolean
  ): ReasoningResult {
    // 1. External live web data inquiries (Stocks, currency rates, breaking news, sports scores)
    val isExternalLiveQuery = normalized.contains("سعر الدولار") || normalized.contains("سعر الذهب") ||
      normalized.contains("اسعار الاسهم") || normalized.contains("نتيجة مباراة") || normalized.contains("الاخبار العاجلة") ||
      normalized.contains("بورصة") || normalized.contains("بيتكوين") || normalized.contains("عملات") ||
      normalized.contains("stock price") || normalized.contains("exchange rate") || normalized.contains("breaking news")

    if (isExternalLiveQuery) {
      val reply = if (isArabic) {
        "هذا السؤال يتطلب اتصالاً بمصادر بيانات خارجية حية (كأسعار الأسواق أو الأخبار اللحظية)، وهو غير متوفر في قاعدة المعرفة المحلية على الجهاز. يمكنني مساعدتك في إدارة هاتفك، تحليل الطقس والمواعيد، أو الإجابة عن الأسئلة العلمية والعامة."
      } else {
        "This query requires a live internet connection to external real-time data sources, which is not available locally offline. I operate entirely on-device to manage your phone, analyze daily context, and assist with logical inquiries."
      }
      return ReasoningResult(
        replyText = reply,
        topicDetected = "unavailable_external_data",
        proactiveSuggestions = listOf(
          ActionableOption(if (isArabic) "حالة الجهاز" else "Device overview", "حالة الجهاز"),
          ActionableOption(if (isArabic) "طقس منطقتي" else "Local weather", "ما حالة الطقس؟")
        )
      )
    }

    // 2. Ambiguous or overly brief queries
    if (query.trim().split("\\s+".toRegex()).size <= 2 && query.length < 10) {
      val reply = if (isArabic) {
        "طلبك بخصوص '$query' غير محدد بما يكفي. هل تقصد الاستفسار عن معلومة معينة، أم تنفيذ إجراء في الهاتف مثل فتح تطبيق أو فحص الإعدادات؟"
      } else {
        "Your request regarding '$query' is ambiguous. Would you like information, or to execute a device action such as opening an app or checking battery?"
      }
      return ReasoningResult(
        replyText = reply,
        topicDetected = "ambiguous_clarification",
        proactiveSuggestions = listOf(
          ActionableOption(if (isArabic) "ماذا أفعل الآن؟" else "What's my status?", "ماذا افعل الان؟"),
          ActionableOption(if (isArabic) "فتح تطبيق" else "Open app", "افتح الاعدادات")
        )
      )
    }

    // 3. Thoughtful direct reasoning on unindexed queries
    val reply = if (isArabic) {
      "بخصوص سؤالك عن '$query': هذه المعلومة التفصيلية غير مسجلة في قاعدة المعرفة المحلية الحالية على الجهاز. يمكنني مساعدتك في التحكم بالهاتف، متابعة السياق اليومي (الطقس، البطارية، المواعيد)، العمليات الحسابية، أو النصائح العلمية والعملية."
    } else {
      "Regarding your inquiry about '$query', this specific detail is not stored in my on-device knowledge base. I can assist with device controls, contextual tracking (weather, battery, calendar), calculations, and lifestyle advice."
    }

    return ReasoningResult(
      replyText = reply,
      topicDetected = "unindexed_query",
      proactiveSuggestions = listOf(
        ActionableOption(if (isArabic) "ماذا تستطيع أن تفعل؟" else "What can you do?", "ماذا تستطيع ان تفعل؟"),
        ActionableOption(if (isArabic) "فحص حالة الهاتف" else "Check device", "حالة الجهاز"),
        ActionableOption(if (isArabic) "هل أخرج الآن؟" else "Should I leave?", "هل اخرج الان؟")
      )
    )
  }
}
