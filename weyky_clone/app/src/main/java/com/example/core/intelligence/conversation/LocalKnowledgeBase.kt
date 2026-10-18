package com.example.core.intelligence.conversation

import java.util.Locale

/**
 * Local on-device structured knowledge base and reasoning bank.
 * Provides curated knowledge across science, geography, history, daily life,
 * culture, productivity, mathematics, and language in both Arabic and English.
 */
object LocalKnowledgeBase {

  data class KnowledgeTopic(
    val id: String,
    val keywords: List<String>,
    val arabicSummary: String,
    val englishSummary: String,
    val suggestedFollowUps: List<ActionableOption>
  )

  private val topics = listOf(
    // 1. Identity & WEYKY purpose
    KnowledgeTopic(
      id = "identity",
      keywords = listOf("من انت", "من أنت", "عرفني بنفسك", "ما هو ويكي", "ما هو وايكي", "who are you", "what are you", "اسمك", "ما هي weyky", "ما هو weyky", "حدثني عن weyky"),
      arabicSummary = "أنا وايكي، النواة الذكية الحية لهاتفك. أعمل محلياً بالكامل لفهم سياقك اليومي، التحكم في وظائف الجهاز، مساعدتك في اتخاذ القرارات اليومية، والتحاور معك بذكاء وخصوصية تامة.",
      englishSummary = "I am Weyky, your contextual intelligent assistant on this device. I work locally to help you understand your daily context, manage your device, reason together, and provide helpful insights.",
      suggestedFollowUps = listOf(
        ActionableOption("ماذا تستطيع أن تفعل؟", "ماذا تستطيع ان تفعل؟"),
        ActionableOption("ما حالة هاتفي الآن؟", "حالة الجهاز"),
        ActionableOption("هل أخرج الآن؟", "هل اخرج الان؟")
      )
    ),
    // 2. Capabilities
    KnowledgeTopic(
      id = "capabilities",
      keywords = listOf("ماذا تستطيع", "قدراتك", "مميزاتك", "شو بتعرف", "what can you do", "features", "كيف تساعدني", "مهامك"),
      arabicSummary = "يمكنني مساعدتك في عدة مجالات:\n1. التحكم الفوري بالهاتف (فتح التطبيقات، الكشاف، الصوت، الموسيقى، المكالمات).\n2. الذكاء السياقي (تحليل الطقس، البطارية، المواعيد، ونصيحة الخروج).\n3. الحوار المعرفي والمنطقي (العلوم، الجغرافيا، الحساب، تنظيم الوقت، والنصائح اليومية) بدون إنترنت.",
      englishSummary = "I can assist you with:\n1. Device controls (apps, flashlight, audio volume, media, calls).\n2. Real-time situational analysis (weather, battery, appointments, departure advice).\n3. Conversational reasoning, calculations, productivity habits, and answering diverse knowledge questions completely offline.",
      suggestedFollowUps = listOf(
        ActionableOption("فحص الطقس", "ما حالة الطقس؟"),
        ActionableOption("فحص البطارية", "كم نسبة البطارية؟"),
        ActionableOption("احسب 25 * 4", "احسب 25 * 4")
      )
    ),
    // 3. Sleep & Health
    KnowledgeTopic(
      id = "sleep_wellness",
      keywords = listOf("النوم", "نوم", "ارق", "أرق", "كيف انام", "نصائح للنوم", "sleep", "insomnia", "راحة", "الاستيقاظ"),
      arabicSummary = "للحصول على نوم عميق وصحي ينصح الخبراء بـ:\n1. تثبيت موعد النوم والاستيقاظ يومياً لضبط الساعة البيولوجية.\n2. الابتعاد عن شاشات الهواتف والضوء الأزرق قبل النوم بـ 30 دقيقة.\n3. الحفاظ على برودة الغرفة وهدوئها (~18-20 درجة مئوية).\n4. تجنب الكافيين والوجبات الثقيلة في المساء.",
      englishSummary = "To achieve deep and restorative sleep, experts recommend:\n1. Keeping a consistent bedtime and wake-up schedule.\n2. Avoiding blue light screens at least 30 minutes before sleep.\n3. Keeping your bedroom cool and dark (around 18-20°C).\n4. Avoiding caffeine and heavy meals late in the evening.",
      suggestedFollowUps = listOf(
        ActionableOption("تأثير القهوة على النوم", "تأثير القهوة على النوم"),
        ActionableOption("إطفاء الكشاف", "اطفئ الكشاف"),
        ActionableOption("خفض الصوت للهدوء", "اخفض الصوت")
      )
    ),
    // 4. Productivity & Time Management (Pomodoro etc.)
    KnowledgeTopic(
      id = "productivity",
      keywords = listOf("تنظيم الوقت", "إدارة الوقت", "التركيز", "الانتاجية", "الإنتاجية", "productivity", "time management", "pomodoro", "بومودورو", "كسل", "تأجيل", "تسويف", "دراسة", "مذاكرة"),
      arabicSummary = "لرفع الإنتاجية والتركيز وتجنب التسويف:\n1. تقنية بومودورو: اعمل بتركيز تام لمدة 25 دقيقة ثم خذ استراحة 5 دقائق.\n2. قاعدة البدء بالأصعب (Eat the Frog): أنجز المهمة الأهم في بداية يومك.\n3. مصفوفة أيزنهاور: قسّم مهامك حسب الأهمية والاستعجال وتخلص من المشتتات.\n4. تقسيم الأهداف الكبيرة إلى خطوات صغيرة قابلة للتنفيذ فوراً.",
      englishSummary = "To boost your productivity and focus:\n1. The Pomodoro Technique: 25 minutes of deep work followed by a 5-minute break.\n2. Eat That Frog: Tackle your most demanding task first thing in the morning.\n3. Eisenhower Matrix: Distinguish between urgent and important, eliminate distractions.\n4. Break large projects into bite-sized, immediate action steps.",
      suggestedFollowUps = listOf(
        ActionableOption("هل لدي مواعيد اليوم؟", "هل سأصل في الموعد؟"),
        ActionableOption("كم الساعة الآن؟", "كم الساعة؟"),
        ActionableOption("نصيحة إضافية للتركيز", "نصيحة للتركيز")
      )
    ),
    // 5. Artificial Intelligence & Local Processing
    KnowledgeTopic(
      id = "ai_concept",
      keywords = listOf("الذكاء الاصطناعي", "ai", "artificial intelligence", "تعلم الآلة", "الشبكات العصبية", "machine learning", "كيف تفكر", "كيف تعمل"),
      arabicSummary = "الذكاء الاصطناعي هو قدرة الأنظمة الحاسوبية على محاكاة القدرات الذهنية البشرية مثل الفهم، الاستنتاج، والتعلم من السياق. في WEYKY، نعتمد على الذكاء المدمج محلياً على الجهاز (On-Device Intelligence) لضمان السرعة الفورية وحماية خصوصية بياناتك بالكامل دون الحاجة لإرسالها إلى خوادم خارجية.",
      englishSummary = "Artificial Intelligence simulates human cognitive capabilities such as learning, deduction, and natural language comprehension. In WEYKY, contextual reasoning and on-device knowledge are integrated locally for maximum privacy and instant speed without relying on external servers.",
      suggestedFollowUps = listOf(
        ActionableOption("فحص ذاكرة الجهاز", "كم الذاكرة المتبقية؟"),
        ActionableOption("حالة السياق الحالي", "ما هو السياق؟")
      )
    ),
    // 6. Geography: Capitals & Countries
    KnowledgeTopic(
      id = "capitals_arab",
      keywords = listOf("عاصمة", "عواصم", "السعودية", "مصر", "الامارات", "عمان", "قطر", "الكويت", "الأردن", "المغرب", "الجزائر", "تونس", "العراق", "سوريا", "لبنان", "فلسطين", "اليمن", "فرنسا", "اليابان", "capital"),
      arabicSummary = "إليك أبرز العواصم العربية والعالمية:\n- السعودية: الرياض\n- الإمارات: أبوظبي\n- مصر: القاهرة\n- سلطنة عُمان: مسقط\n- قطر: الدوحة\n- الكويت: مدينة الكويت\n- فرنسا: باريس\n- بريطانيا: لندن\n- اليابان: طوكيو",
      englishSummary = "Key world capitals:\n- Saudi Arabia: Riyadh\n- UAE: Abu Dhabi\n- Egypt: Cairo\n- Oman: Muscat\n- Qatar: Doha\n- Kuwait: Kuwait City\n- France: Paris\n- United Kingdom: London\n- Japan: Tokyo",
      suggestedFollowUps = listOf(
        ActionableOption("حدثني عن صلالة", "حدثني عن صلالة"),
        ActionableOption("ما حالة الطقس عندي؟", "ما حالة الطقس؟")
      )
    ),
    // 7. Salalah & Tourism / Seasonal Travel
    KnowledgeTopic(
      id = "salalah_tourism",
      keywords = listOf("صلالة", "صلاله", "خريف صلالة", "ظفار", "سياحة عمان", "سفر", "salalah"),
      arabicSummary = "صلالة هي حاضرة محافظة ظفار في سلطنة عُمان، وتشتهر بموسم 'الخريف' (من أواخر يونيو إلى سبتمبر) حيث تتحول الجبال والسهول إلى بساط أخضر ساحر مع ضباب ورذاذ منعش ودرجات حرارة معتدلة (~25°م) بينما يكون الصيف حاراً في بقية المنطقة. من أبرز معالمها: وادي دربات، عين أثوم، وشاطئ المغسيل.",
      englishSummary = "Salalah is the jewel of Dhofar, Oman. It is famous for the 'Khareef' monsoon season (late June to September), when the mountains turn lush green under refreshing mist and cool temperatures around 25°C. Top attractions include Wadi Darbat, Ayn Athum, and Mughsail Beach.",
      suggestedFollowUps = listOf(
        ActionableOption("ما أفضل وقت لزيارتها؟", "متى أفضل وقت لزيارة صلالة؟"),
        ActionableOption("طقس منطقتي الآن", "ما حالة الطقس؟")
      )
    ),
    // 8. Science: Speed of light, Solar System, Gravity
    KnowledgeTopic(
      id = "physics_universe",
      keywords = listOf("سرعة الضوء", "المجموعة الشمسية", "الجاذبية", "الفضاء", "الكواكب", "الشمس", "speed of light", "solar system", "gravity", "الكون"),
      arabicSummary = "حقائق علمية كونية:\n- سرعة الضوء في الفراغ تبلغ حوالي 300,000 كيلومتر في الثانية (299,792 كم/ث).\n- يستغرق ضوء الشمس حوالي 8 دقائق و20 ثانية للوصول إلى الأرض.\n- تتكون مجموعتنا الشمسية من 8 كواكب، أكبرها المشتري وأقربها للشمس عطارد.\n- الجاذبية هي القوة التي تحفظ الكواكب في مداراتها وتمنح الأجسام أوزانها.",
      englishSummary = "Fascinating cosmic facts:\n- The speed of light in a vacuum is approximately 300,000 km/s (299,792 km/s).\n- Sunlight takes about 8 minutes and 20 seconds to reach Earth.\n- Our solar system contains 8 recognized planets, Jupiter being the largest and Mercury closest to the Sun.\n- Gravity is the fundamental attraction keeping planets in orbit and holding our atmosphere.",
      suggestedFollowUps = listOf(
        ActionableOption("احسب المسافة في دقيقة", "احسب 300000 * 60"),
        ActionableOption("احكِ لي قصة قصيرة", "احك لي قصة قصيرة")
      )
    ),
    // 9. Short Inspirational Story / Wisdom
    KnowledgeTopic(
      id = "short_story",
      keywords = listOf("قصة", "احك لي قصة", "قصة قصيرة", "حكمة", "موعظة", "tell me a story", "طرفة", "الهام", "إلهام"),
      arabicSummary = "قصة قصيرة ملهمة: يُحكى أن مزارعاً كان يفوز دائماً بجائزة أفضل محصول ذرة، وكان يوزع أفضل بذوره على جيرانه المزارعين! وعندما سُئل عن السبب قال: 'الرياح تنقل حبوب اللقاح بين الحقول، فإذا زرع جيراني ذرة رديئة ستؤثر على جودة محصولي. لكي أحصل على أفضل حصاد، يجب أن أساعد جيراني على زراعة الأفضل أيضاً'. الحكمة: نجاحك الحقيقي يزدهر عندما تساعد من حولك على النجاح.",
      englishSummary = "A wise farmer was asked why he always shared his finest seeds with his neighbors. He smiled and said: 'The wind carries pollen from field to field. If my neighbors grow inferior grain, cross-pollination will degrade my own crops. By helping them succeed, I enrich my harvest.' Lesson: True excellence thrives when everyone around us prospers.",
      suggestedFollowUps = listOf(
        ActionableOption("نصائح للإنتاجية", "تنظيم الوقت"),
        ActionableOption("نصائح للنوم الصحي", "نصائح للنوم")
      )
    ),
    // 10. Healthy Habits & Water
    KnowledgeTopic(
      id = "hydration_health",
      keywords = listOf("شرب الماء", "الماء", "فوائد الماء", "صحة الجسم", "water intake", "ترطيب", "الصداع"),
      arabicSummary = "يشكل الماء حوالي 60% من جسم الإنسان، وشرب كمية كافية (حوالي 2 إلى 3 لترات يومياً) يساعد على:\n1. تنشيط الذاكرة والتركيز ومنع الصداع الناتج عن الجفاف.\n2. تنظيم درجة حرارة الجسم وتحسين الهضم.\n3. الحفاظ على نضارة البشرة ونشاط العضلات.",
      englishSummary = "Water constitutes about 60% of body weight and is critical for brain function, circulation, and temperature regulation. It is recommended to drink 2 to 3 liters daily (around 8 glasses), increasing during hot weather or physical exercise.",
      suggestedFollowUps = listOf(
        ActionableOption("فحص حرارة الطقس", "ما حالة الطقس؟"),
        ActionableOption("نصائح للنوم", "نصائح للنوم")
      )
    ),
    // 11. Battery & Device Care
    KnowledgeTopic(
      id = "battery_care",
      keywords = listOf("الحفاظ على البطارية", "عمر البطارية", "توفير البطارية", "battery health", "شحن الهاتف", "بطارية الهاتف"),
      arabicSummary = "للحفاظ على صحة بطارية هاتفك لأطول فترة:\n1. حاول إبقاء نسبة الشحن بين 20% و80% وتجنب التفريغ الكامل المتكرر.\n2. تجنب تعريض الهاتف للحرارة العالية أثناء الشحن.\n3. خفّض سطوع الشاشة واستخدم الوضع الداكن (Dark Mode).\n4. أغلق البلوتوث والموقع عند عدم الحاجة إليهما.",
      englishSummary = "To maximize your phone's battery lifespan:\n1. Keep charge levels between 20% and 80%, avoiding complete depletion.\n2. Avoid exposing your device to high heat or direct sunlight while charging.\n3. Lower screen brightness and use Dark Mode.\n4. Disable Bluetooth and GPS when not in active use.",
      suggestedFollowUps = listOf(
        ActionableOption("كم نسبة بطاريتي الآن؟", "كم نسبة البطارية؟"),
        ActionableOption("هل البطارية ستكفي؟", "هل البطارية ستكفي؟")
      )
    )
  )

  fun normalizeArabic(raw: String): String {
    return raw.trim()
      .lowercase(Locale.ROOT)
      .replace('أ', 'ا')
      .replace('إ', 'ا')
      .replace('آ', 'ا')
      .replace('ة', 'ه')
      .replace('ى', 'ي')
      .replace('ؤ', 'و')
      .replace('ئ', 'ي')
      .replace('؟', ' ')
      .replace('?', ' ')
      .replace('!', ' ')
      .replace('.', ' ')
      .replace(',', ' ')
  }

  /**
   * Looks up a topic based on keywords in the query.
   */
  fun findMatchingTopic(query: String): KnowledgeTopic? {
    val normQuery = normalizeArabic(query)
    for (topic in topics) {
      for (kw in topic.keywords) {
        val normKw = normalizeArabic(kw)
        if (normQuery.contains(normKw)) {
          return topic
        }
      }
    }
    return null
  }

  /**
   * Evaluates simple arithmetic expressions locally.
   * e.g. "احسب 25 * 4", "كم يساوي 15 + 30", "120 / 6", "15 * 8"
   */
  fun evaluateArithmetic(raw: String): String? {
    val clean = raw.replace("احسب", "")
      .replace("كم يساوي", "")
      .replace("ما ناتج", "")
      .replace("calculate", "", ignoreCase = true)
      .replace("math", "", ignoreCase = true)
      .replace("ضرب", "*")
      .replace("في", "*")
      .replace("×", "*")
      .replace("زائد", "+")
      .replace("ناقص", "-")
      .replace("قسمة", "/")
      .replace("على", "/")
      .replace("÷", "/")
      .replace("x", "*", ignoreCase = true)
      .replace("X", "*")
      .trim()

    val regex = Regex("""([0-9]+(?:\.[0-9]+)?)\s*([\+\-\*\/])\s*([0-9]+(?:\.[0-9]+)?)""")
    val match = regex.find(clean) ?: return null

    val (num1Str, op, num2Str) = match.destructured
    val n1 = num1Str.toDoubleOrNull() ?: return null
    val n2 = num2Str.toDoubleOrNull() ?: return null

    val res = when (op) {
      "+" -> n1 + n2
      "-" -> n1 - n2
      "*" -> n1 * n2
      "/" -> if (n2 != 0.0) n1 / n2 else return "لا يمكن القسمة على صفر."
      else -> return null
    }

    val isArabic = Locale.getDefault().language.startsWith("ar")
    val formattedResult = if (res == res.toLong().toDouble()) res.toLong().toString() else String.format(Locale.US, "%.2f", res)

    return if (isArabic) {
      "الناتج الحسابي: $num1Str $op $num2Str = $formattedResult"
    } else {
      "Result: $num1Str $op $num2Str = $formattedResult"
    }
  }
}
