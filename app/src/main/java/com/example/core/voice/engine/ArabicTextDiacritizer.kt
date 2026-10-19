package com.example.core.voice.engine

import java.text.DecimalFormat

/**
 * High-quality Arabic text diacritizer, vocalization, and normalizer for TTS.
 * Functions:
 * 1. Expands Arabic digits (١، ٢، ٣) and Western digits (1, 2, 3) to fully diacritized Arabic words.
 * 2. Normalizes punctuation, percentages, units, mathematical symbols, dates, and times.
 * 3. Applies Rule-Based / Morphological diacritics (تشكيل) to common assistant vocabulary, particles,
 *    greetings, conjunctions, pronouns, and prepositions if the input is not pre-diacritized.
 * 4. Preserves already diacritized Arabic text untouched.
 */
object ArabicTextDiacritizer {

  private val ARABIC_DIACRITICS_REGEX = Regex("[\u064B-\u0652\u0670]")

  /**
   * Checks if the text is already partially or fully vocalized (contains tashkeel marks).
   */
  fun isPreDiacritized(text: String): Boolean {
    val diacriticCount = ARABIC_DIACRITICS_REGEX.findAll(text).count()
    val arabicLettersCount = text.count { char ->
      char in '\u0621'..'\u064A'
    }
    if (arabicLettersCount == 0) return false
    // If more than 15% of characters have diacritics, consider it intentionally vocalized
    return (diacriticCount.toDouble() / arabicLettersCount.toDouble()) > 0.15
  }

  /**
   * Main entry point to vocalize and prepare Arabic text for speech synthesis engines.
   */
  fun prepareAndDiacritize(raw: String): String {
    if (raw.isBlank()) return ""

    // Step 1: Expand dates, clock times, decimals, symbols, units, percentages
    var text = expandDatesAndTimes(raw)
    text = expandSymbolsAndUnits(text)

    // Step 2: Convert decimals and integers to spoken Arabic words
    text = expandNumbers(text)

    // Step 3: If already heavily diacritized by user / prompt, preserve and return
    if (isPreDiacritized(text)) {
      return cleanPunctuationForSpeech(text)
    }

    // Step 4: Apply high-frequency rule-based diacritization on common particles, verbs, and assistant patterns
    text = applyContextualDiacritization(text)

    // Step 5: Clean punctuation for natural speech pauses
    return cleanPunctuationForSpeech(text)
  }

  /**
   * Splits long Arabic text into natural sentence/clause chunks suitable for low-memory
   * neural VITS ONNX inference on mid-range phones (e.g. Huawei P40 Lite E & Samsung A24),
   * preserving exact clause order and avoiding long unnatural pauses.
   */
  fun splitIntoSynthesisChunks(text: String, maxCharsPerChunk: Int = 120): List<String> {
    val normalized = text.replace(Regex("\\s+"), " ").trim()
    if (normalized.isEmpty()) return emptyList()
    if (normalized.length <= maxCharsPerChunk) return listOf(normalized)

    val rawClauses = normalized.split(Regex("(?<=[.!?؟،؛\\n])\\s+"))
      .map { it.trim() }
      .filter { it.isNotEmpty() }

    val chunks = mutableListOf<String>()
    val current = StringBuilder()

    for (clause in rawClauses) {
      if (clause.length > maxCharsPerChunk) {
        if (current.isNotEmpty()) {
          chunks.add(current.toString().trim())
          current.clear()
        }
        // Further split very long punctuation-free clause by words
        val words = clause.split(" ")
        for (word in words) {
          if (current.length + word.length + 1 > maxCharsPerChunk && current.isNotEmpty()) {
            chunks.add(current.toString().trim())
            current.clear()
          }
          if (current.isNotEmpty()) current.append(' ')
          current.append(word)
        }
      } else if (current.length + clause.length + 1 <= maxCharsPerChunk) {
        if (current.isNotEmpty()) current.append(' ')
        current.append(clause)
      } else {
        if (current.isNotEmpty()) {
          chunks.add(current.toString().trim())
          current.clear()
        }
        current.append(clause)
      }
    }

    if (current.isNotEmpty()) {
      chunks.add(current.toString().trim())
    }
    return chunks
  }

  private fun expandDatesAndTimes(text: String): String {
    val standardized = standardizeEasternDigits(text)
    // Expand YYYY/MM/DD or DD/MM/YYYY dates before general slash replacement
    val dateRegex = Regex("""\b(\d{1,4})[/-](\d{1,2})[/-](\d{1,4})\b""")
    val afterDates = dateRegex.replace(standardized) { m ->
      val p1 = m.groupValues[1].toLongOrNull()
      val p2 = m.groupValues[2].toIntOrNull()
      val p3 = m.groupValues[3].toLongOrNull()
      val monthNames = arrayOf(
        "", "يَنَايِر", "فِبْرَايِر", "مَارِس", "أَبْرِيل", "مَايُو", "يُونْيُو",
        "يُولْيُو", "أَغُسْطُس", "سِبْتَمْبَر", "أُكْتُوبَر", "نُوفَمْبَر", "دِيسَمْبَر"
      )
      if (p1 != null && p2 != null && p3 != null && p2 in 1..12) {
        val monthWord = monthNames[p2]
        if (p1 > 31) {
          // YYYY-MM-DD
          "${numberToArabicWords(p3)} $monthWord ${numberToArabicWords(p1)}"
        } else {
          // DD-MM-YYYY
          "${numberToArabicWords(p1)} $monthWord ${numberToArabicWords(p3)}"
        }
      } else {
        m.value
      }
    }

    // Expand HH:MM clock times (e.g. 14:30 -> الرابعة عشرة و ثلاثون دقيقة)
    val timeRegex = Regex("""\b(\d{1,2}):(\d{2})\b""")
    return timeRegex.replace(afterDates) { m ->
      val hours = m.groupValues[1].toLongOrNull()
      val mins = m.groupValues[2].toLongOrNull()
      if (hours != null && mins != null && hours in 0..23 && mins in 0..59) {
        if (mins == 0L) {
          "السَّاعَةُ ${numberToArabicWords(hours)} تَمَامًا"
        } else {
          "${numberToArabicWords(hours)} وَ ${numberToArabicWords(mins)} دَقِيقَةً"
        }
      } else {
        m.value
      }
    }
  }

  private fun expandSymbolsAndUnits(text: String): String {
    return text
      .replace(Regex("""(?i)\bweyky\s*\(\s*(?:وايكي|ويكي)\s*\)"""), " وَايْكِي ")
      .replace(Regex("""(?i)\bweyky\b"""), " وَايْكِي ")
      // Speed units with numbers or standalone: e.g. 24كم\س, 24كم/س, 24 كم\س, 24 كم/س, 100كلم/س, 80 km/h, etc.
      .replace(Regex("""(?i)(?<=\d)\s*(?:ك(?:ي)?ل?م|km)\s*[/\\|]\s*(?:س(?:اع(?:ة|ه))?|h(?:r)?)(?!\w)"""), " كِيلُومِتْرًا فِي السَّاعَةِ ")
      .replace(Regex("""(?i)(?:ك(?:ي)?ل?م|km)\s*[/\\|]\s*(?:س(?:اع(?:ة|ه))?|h(?:r)?)(?!\w)"""), " كِيلُومِتْر فِي السَّاعَةِ ")
      .replace(Regex("""(?i)\b(?:kmph|kph|kmh)\b"""), " كِيلُومِتْرًا فِي السَّاعَةِ ")
      // Acceleration / speed in m/s: e.g. 10م/ث, 10م\ث, 10 m/s
      .replace(Regex("""(?i)(?<=\d)\s*(?:م|m)\s*[/\\|]\s*(?:ث(?:ان(?:ية|ه))?|s(?:ec)?)(?!\w)"""), " مِتْرًا فِي الثَّانِيَةِ ")
      .replace(Regex("""(?i)(?:م|m)\s*[/\\|]\s*(?:ث(?:ان(?:ية|ه))?|s(?:ec)?)(?!\w)"""), " مِتْر فِي الثَّانِيَةِ ")
      // Distance units after digits: e.g. 24كم, 24 كم, 50كلم
      .replace(Regex("""(?i)(?<=\d)\s*(?:ك(?:ي)?ل?م|km)(?!\w)"""), " كِيلُومِتْرًا ")
      // Mass & dimensions after digits
      .replace(Regex("""(?i)(?<=\d)\s*(?:كغ|كجم|kg)\b"""), " كِيلُوجْرَام ")
      .replace(Regex("""(?i)(?<=\d)\s*(?:غم|g)\b"""), " جْرَام ")
      .replace(Regex("""(?i)(?<=\d)\s*(?:سم|cm)\b"""), " سَنْتِيمِتْر ")
      .replace(Regex("""(?i)(?<=\d)\s*(?:ملم|mm)\b"""), " مِلِّيمِتْر ")
      // Temperatures & percentages
      .replace("%", " بِالْمِئَةِ ")
      .replace("٪", " بِالْمِئَةِ ")
      .replace(Regex("""(?i)°\s*[cCمsS]|°[cC]"""), " دَرَجَةً مِئَوِيَّةً ")
      .replace(Regex("""(?<=\d)\s*°"""), " دَرَجَةً مِئَوِيَّةً ")
      // Data units
      .replace(Regex("""(?i)\bMB\b"""), " مِيجَابَايْت ")
      .replace(Regex("""(?i)\bGB\b"""), " جِيجَابَايْت ")
      .replace(Regex("""(?i)\bKB\b"""), " كِيلُوبَايْت ")
      // Math operators & slashes
      .replace(" / ", " مِنْ أَصْلِ ")
      .replace(" + ", " زَائِد ")
      .replace(" - ", " نَاقِص ")
      .replace(" = ", " يُسَاوِي ")
      .replace("&", " وَ ")
      .replace("\\", " ")
  }

  private fun standardizeEasternDigits(text: String): String {
    return buildString(text.length) {
      for (ch in text) {
        when (ch) {
          '٠' -> append('0'); '١' -> append('1'); '٢' -> append('2'); '٣' -> append('3'); '٤' -> append('4')
          '٥' -> append('5'); '٦' -> append('6'); '٧' -> append('7'); '٨' -> append('8'); '٩' -> append('9')
          else -> append(ch)
        }
      }
    }
  }

  /**
   * Finds decimal and integer numbers in text and replaces them with Arabic vocalized words.
   */
  private fun expandNumbers(text: String): String {
    val standardized = standardizeEasternDigits(text)

    // First expand decimal numbers (e.g., 36.5 -> ستة وثلاثون فاصل خمسة)
    val decimalRegex = Regex("""\b(\d+)[.,](\d+)\b""")
    val afterDecimals = decimalRegex.replace(standardized) { matchResult ->
      val intPart = matchResult.groupValues[1].toLongOrNull()
      val fracStr = matchResult.groupValues[2]
      val fracPart = fracStr.toLongOrNull()
      if (intPart != null && fracPart != null && intPart in 0..999999 && fracPart in 0..999999) {
        " ${numberToArabicWords(intPart)} فَاصِل ${numberToArabicWords(fracPart)} "
      } else {
        matchResult.value
      }
    }

    // Match integers up to 999,999,999
    val numberRegex = Regex("\\b\\d+\\b")
    return numberRegex.replace(afterDecimals) { matchResult ->
      val numStr = matchResult.value
      val num = numStr.toLongOrNull()
      if (num != null && num in 0..999999) {
        " " + numberToArabicWords(num) + " "
      } else {
        matchResult.value
      }
    }
  }

  fun numberToArabicWords(number: Long): String {
    if (number == 0L) return "صِفْر"
    if (number < 0L) return "سَالِب " + numberToArabicWords(-number)

    val units = arrayOf(
      "", "وَاحِد", "اِثْنَان", "ثَلَاثَة", "أَرْبَعَة",
      "خَمْسَة", "سِتَّة", "سَبْعَة", "ثَمَانِيَة", "تِسْعَة"
    )
    val teens = arrayOf(
      "عَشَرَة", "أَحَدَ عَشَرَ", "اِثْنَا عَشَرَ", "ثَلَاثَةَ عَشَرَ", "أَرْبَعَةَ عَشَرَ",
      "خَمْسَةَ عَشَرَ", "سِتَّةَ عَشَرَ", "سَبْعَةَ عَشَرَ", "ثَمَانِيَةَ عَشَرَ", "تِسْعَةَ عَشَرَ"
    )
    val tens = arrayOf(
      "", "", "عِشْرُونَ", "ثَلَاثُونَ", "أَرْبَعُونَ",
      "خَمْسُونَ", "سِتُّونَ", "سَبْعُونَ", "ثَمَانُونَ", "تِسْعُونَ"
    )
    val hundreds = arrayOf(
      "", "مِئَة", "مِئَتَان", "ثَلَاثُمِئَة", "أَرْبَعُمِئَة",
      "خَمْسُمِئَة", "سِتُّمِئَة", "سَبْعُمِئَة", "ثَمَانُمِئَة", "تِسْعُمِئَة"
    )

    fun convertUnder1000(n: Int): String {
      if (n == 0) return ""
      val h = n / 100
      val rem = n % 100
      val parts = mutableListOf<String>()
      if (h > 0) parts.add(hundreds[h])

      if (rem in 1..9) {
        parts.add(units[rem])
      } else if (rem in 10..19) {
        parts.add(teens[rem - 10])
      } else if (rem >= 20) {
        val u = rem % 10
        val t = rem / 10
        if (u > 0) {
          parts.add("${units[u]} وَ${tens[t]}")
        } else {
          parts.add(tens[t])
        }
      }
      return parts.joinToString(" وَ")
    }

    if (number < 1000L) {
      return convertUnder1000(number.toInt())
    }

    val thousands = (number / 1000L).toInt()
    val remainder = (number % 1000L).toInt()

    val thousandsWord = when (thousands) {
      1 -> "أَلْف"
      2 -> "أَلْفَان"
      in 3..10 -> "${units[thousands]} آلَاف"
      else -> "${convertUnder1000(thousands)} أَلْف"
    }

    val remWord = if (remainder > 0) " وَ" + convertUnder1000(remainder) else ""
    return thousandsWord + remWord
  }

  /**
   * Replaces common Arabic assistant words and particles with vocalized equivalents
   * ensuring accurate phonemes in acoustic models.
   */
  private fun applyContextualDiacritization(input: String): String {
    // Map of whole words with exact vocalization
    val vocalizedDictionary = mapOf(
      "اهلا" to "أَهْلًا",
      "أهلا" to "أَهْلًا",
      "مرحبا" to "مَرْحَبًا",
      "السلام" to "السَّلَامُ",
      "عليكم" to "عَلَيْكُمْ",
      "وعليكم" to "وَعَلَيْكُمُ",
      "ورحمة" to "وَرَحْمَةُ",
      "الله" to "اللَّهِ",
      "صباح" to "صَبَاحُ",
      "الخير" to "الْخَيْرِ",
      "النور" to "النُّورِ",
      "مساء" to "مَسَاءُ",
      "شكرا" to "شُكْرًا",
      "عفوا" to "عَفْوًا",
      "انا" to "أَنَا",
      "أَنَا" to "أَنَا",
      "انت" to "أَنْتَ",
      "أنت" to "أَنْتَ",
      "هذا" to "هَذَا",
      "هذه" to "هَذِهِ",
      "ذلك" to "ذَلِكَ",
      "تلك" to "تِلْكَ",
      "هنا" to "هُنَا",
      "هناك" to "هُنَاكَ",
      "في" to "فِي",
      "من" to "مِنْ",
      "الى" to "إِلَى",
      "إلى" to "إِلَى",
      "على" to "عَلَى",
      "عن" to "عَنْ",
      "مع" to "مَعَ",
      "بين" to "بَيْنَ",
      "بعد" to "بَعْدَ",
      "قبل" to "قَبْلَ",
      "عند" to "عِنْدَ",
      "الان" to "الْآنَ",
      "الآن" to "الْآنَ",
      "اليوم" to "الْيَوْمَ",
      "غدا" to "غَدًا",
      "أمس" to "أَمْسِ",
      "نعم" to "نَعَمْ",
      "لا" to "لَا",
      "هل" to "هَلْ",
      "كيف" to "كَيْفَ",
      "متى" to "مَتَى",
      "اين" to "أَيْنَ",
      "أين" to "أَيْنَ",
      "لماذا" to "لِمَاذَا",
      "ماذا" to "مَاذَا",
      "كم" to "كَمْ",
      "البطارية" to "الْبَطَّارِيَّةُ",
      "بطارية" to "بَطَّارِيَّةُ",
      "الشحن" to "الشَّحْنِ",
      "الطقس" to "الطَّقْسُ",
      "الجو" to "الْجَوُّ",
      "الحرارة" to "الْحَرَارَةُ",
      "درجة" to "دَرَجَةُ",
      "الكشاف" to "الْكَشَّافُ",
      "الصوت" to "الصَّوْتُ",
      "الموسيقى" to "الْمُوسِيقَى",
      "الهاتف" to "الْهَاتِفُ",
      "الجهاز" to "الْجِهَازُ",
      "الذاكرة" to "الذَّاكِرَةُ",
      "التخزين" to "التَّخْزِينُ",
      "الشبكة" to "الشَّبَكَةُ",
      "مكالمة" to "مُكَالَمَةٌ",
      "فائتة" to "فَائِتَةٌ",
      "موعد" to "مَوْعِدٌ",
      "دقيقة" to "دَقِيقَةً",
      "دقائق" to "دَقَائِقَ",
      "ساعة" to "سَاعَةً",
      "تم" to "تَمَّ",
      "تشغيل" to "تَشْغِيلُ",
      "إيقاف" to "إِيقَافُ",
      "فتح" to "فَتْحُ",
      "تطبيق" to "تَطْبِيقِ",
      "ويكي" to "وَايْكِي",
      "وايكي" to "وَايْكِي",
      "WEYKY" to "وَايْكِي",
      "Weyky" to "وَايْكِي",
      "weyky" to "وَايْكِي"
    )

    val words = input.split(Regex("\\s+"))
    return words.joinToString(" ") { word ->
      // Strip leading/trailing punctuation if attached
      val cleanWord = word.trim { !it.isLetterOrDigit() && it !in '\u064B'..'\u0652' }
      val vocalized = vocalizedDictionary[cleanWord]
      if (vocalized != null) {
        word.replace(cleanWord, vocalized)
      } else {
        word
      }
    }
  }

  private fun cleanPunctuationForSpeech(text: String): String {
    return text
      .replace(Regex("""[/\\|]+"""), " ")
      .replace(Regex("[,،]+"), " ، ")
      .replace(Regex("[.!?؟]+"), " . ")
      .replace(Regex("\\s+"), " ")
      .trim()
  }

  private val lexiconLock = Any()

  @Volatile
  private var cachedEspeakLexicon: Map<String, String>? = null

  private val THICK_ARABIC_CONSONANTS = setOf('ص', 'ض', 'ط', 'ظ')
  private val SUN_ARABIC_CONSONANTS = setOf('ت', 'ث', 'د', 'ذ', 'ر', 'ز', 'س', 'ش', 'ص', 'ض', 'ط', 'ظ', 'ل', 'ن')
  private val SHORT_VOWELS = setOf('\u064E', '\u064F', '\u0650')

  private val CORE_BUILTIN_ESPEAK_LEXICON = mapOf(
    "مرحبا" to "m'aRHaban",
    "أهلا" to "?'ahlan",
    "اهلا" to "?'ahlan",
    "بكم" to "b'ikum",
    "بك" to "b'ika",
    "في" to "fi:",
    "من" to "min",
    "إلى" to "?'ila:",
    "الى" to "?'ila:",
    "على" to "A'ala:",
    "عن" to "A'an",
    "مع" to "m'aA",
    "بين" to "b'ajna",
    "بعد" to "b'aAd",
    "قبل" to "q'abl",
    "عند" to "A'ind",
    "الآن" to "?al?'a:na",
    "الان" to "?al?'a:na",
    "اليوم" to "?alj'awma",
    "يوم" to "j'awm",
    "غدا" to "Q'adan",
    "أمس" to "?'amsi",
    "نعم" to "n'aAam",
    "لا" to "l'a:",
    "ما" to "m'a:",
    "ماذا" to "m'a:Da:",
    "لماذا" to "lim'a:Da:",
    "كيف" to "k'ajfa",
    "متى" to "m'ata:",
    "أين" to "?'ajna",
    "اين" to "?'ajna",
    "هل" to "h'al",
    "كم" to "k'am",
    "أنا" to "'ana",
    "انا" to "'ana",
    "أنت" to "?'anta",
    "انت" to "?'anta",
    "هذا" to "ha:Da:",
    "هذه" to "ha:Dihi",
    "ذلك" to "Da:lika",
    "تلك" to "t'ilka",
    "هنا" to "h'una:",
    "هناك" to "hun'a:ka",
    "وايكي" to "w'a:jki:",
    "ويكي" to "w'a:jki:",
    "تفضل" to "taf'adH:al",
    "شكرا" to "S'ukRan",
    "عفوا" to "A'afwan",
    "السلام" to "?assal'a:mu",
    "عليكم" to "Aal'ajkum",
    "وعليكم" to "waAal'ajkumu",
    "ورحمة" to "waR'aHmatu",
    "الله" to "?all'a.:hi",
    "صباح" to "s['a.ba:Hu",
    "مساء" to "mas'a:?u",
    "الخير" to "?alx'ajRi",
    "النور" to "?ann'u:Ri",
    "درجة" to "d'aRadZa",
    "حرارة" to "HaR'a:Ra",
    "الحرارة" to "?alHaR'a:Ra",
    "الجو" to "?aldZ'aw:",
    "جو" to "dZ'aw:",
    "الطقس" to "?at[:'a.qs",
    "طقس" to "t['a.qs",
    "معتدل" to "muAt'adil",
    "معتدلة" to "muAt'adila",
    "مدينة" to "mad'i:na",
    "مدينتك" to "mad'i:natika",
    "مدينتكم" to "mad'i:natikum",
    "مدينتي" to "mad'i:nati:",
    "البطارية" to "?albat[:a:R'ij:a",
    "بطارية" to "bat[:a:R'ij:a",
    "بطاريتك" to "bat[:a:R'ij:atuka",
    "بطاريتي" to "bat[:a:R'ij:ati:",
    "والبطارية" to "walbat[:a:R'ij:a",
    "مشحونة" to "maSH'u:na",
    "مشحون" to "maSH'u:n",
    "الشحن" to "?aS:'aHn",
    "شحن" to "S'aHn",
    "نسبة" to "n'isba",
    "بنسبة" to "bin'isbat",
    "بالمئة" to "bilm'i?a",
    "بالمئه" to "bilm'i?a",
    "مئوية" to "mi?aw'ij:a",
    "صفر" to "s['i.fR",
    "واحد" to "w'a:Hid",
    "واحدة" to "w'a:Hida",
    "اثنان" to "?iTn'a:n",
    "اثنا" to "?iTn'a:",
    "ثلاث" to "Tal'a:T",
    "ثلاثة" to "Tal'a:Ta",
    "أربع" to "?'aRbaA",
    "أربعة" to "?aRb'aAa",
    "خمس" to "x'ams",
    "خمسة" to "x'amsa",
    "ست" to "s'it:",
    "ستة" to "s'it:a",
    "سبع" to "s'abA",
    "سبعة" to "s'abAa",
    "ثماني" to "Tam'a:ni:",
    "ثمانية" to "Tam'a:nija",
    "تسع" to "t'isA",
    "تسعة" to "t'isAa",
    "عشر" to "A'aSaR",
    "عشرة" to "A'aSaRa",
    "عشرون" to "AiSR'u:n",
    "ثلاثون" to "Tala:T'u:n",
    "أربعون" to "?aRbaA'u:n",
    "خمسون" to "xams'u:n",
    "ستون" to "sit:'u:n",
    "سبعون" to "sabA'u:n",
    "ثمانون" to "Tama:n'u:n",
    "تسعون" to "tisA'u:n",
    "مئة" to "m'i?a",
    "مئتان" to "mi?at'a:n",
    "ثلاثمئة" to "Tal'a:Tumi?a",
    "أربعمئة" to "?'aRbaAumi?a",
    "خمسمئة" to "x'amsumi?a",
    "ستمئة" to "s'it:umi?a",
    "سبعمئة" to "s'abAumi?a",
    "ثمانمئة" to "Tam'a:numi?a",
    "تسعمئة" to "t'isAumi?a",
    "ألف" to "?'alf",
    "ألفان" to "?alf'a:n",
    "آلاف" to "?a:l'a:f",
    "فاصل" to "f'a.:s[il",
    "زائد" to "z'a:?id",
    "ناقص" to "n'a.:qis[",
    "يساوي" to "jus'a:wi:",
    "أصل" to "?'as[l",
    "الساعة" to "?ass'a:Aa",
    "ساعة" to "s'a:Aa",
    "دقيقة" to "daq'i:qa",
    "دقائق" to "daq'a.:?iq",
    "تماما" to "tam'a:man",
    "الكشاف" to "?alkaS:'a:f",
    "كشاف" to "kaS:'a:f",
    "الصوت" to "?as[:'a.wt",
    "صوت" to "s['a.wt",
    "الموسيقى" to "?almu:s'i:qa:",
    "موسيقى" to "mu:s'i:qa:",
    "الهاتف" to "?alh'a:tif",
    "هاتف" to "h'a:tif",
    "الجهاز" to "?aldZih'a:z",
    "جهاز" to "dZih'a:z",
    "الذاكرة" to "?aD:'a:kiRa",
    "ذاكرة" to "D'a:kiRa",
    "التخزين" to "?attaxz'i:n",
    "تخزين" to "taxz'i:n",
    "الشبكة" to "?aS:'abaka",
    "شبكة" to "S'abaka",
    "تم" to "t'am:a",
    "تشغيل" to "taSQ'i:l",
    "إيقاف" to "?i:q'a:f",
    "فتح" to "f'atH",
    "تطبيق" to "tat[b'i.:q",
    "أستمع" to "?ast'amiAu",
    "إليك" to "?il'ajka",
    "ميجابايت" to "mi:ga:b'a:jt",
    "جيجابايت" to "dZi:ga:b'a:jt",
    "كيلوبايت" to "ki:lu:b'a:jt",
    "كيلومتر" to "ki:lu:m'itR",
    "واي" to "w'a:j",
    "فاي" to "f'a:j",
    "بلوتوث" to "blu:t'u:T",
    "البلوتوث" to "?alblu:t'u:T"
  )

  private fun getEspeakLexicon(): Map<String, String> {
    val existing = cachedEspeakLexicon
    if (existing != null) return existing
    synchronized(lexiconLock) {
      val again = cachedEspeakLexicon
      if (again != null) return again

      val map = HashMap<String, String>(32000)
      map.putAll(CORE_BUILTIN_ESPEAK_LEXICON)

      // Locate ar_espeak_lexicon.gz from standard workspace or classpath locations
      val candidateFiles = listOf(
        java.io.File("app/src/main/assets/tts/ar_espeak_lexicon.gz"),
        java.io.File("src/main/assets/tts/ar_espeak_lexicon.gz"),
        java.io.File("../app/src/main/assets/tts/ar_espeak_lexicon.gz")
      )
      val file = candidateFiles.firstOrNull { it.exists() && it.isFile }
      try {
        val stream = when {
          file != null -> java.io.FileInputStream(file)
          else -> ArabicTextDiacritizer::class.java.classLoader?.getResourceAsStream("assets/tts/ar_espeak_lexicon.gz")
            ?: ArabicTextDiacritizer::class.java.classLoader?.getResourceAsStream("tts/ar_espeak_lexicon.gz")
        }
        if (stream != null) {
          java.util.zip.GZIPInputStream(stream).bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (line in lines) {
              val tabIdx = line.indexOf('\t')
              if (tabIdx > 0 && tabIdx < line.length - 1) {
                val word = line.substring(0, tabIdx)
                val mnem = line.substring(tabIdx + 1)
                if (!map.containsKey(word)) {
                  map[word] = mnem
                }
              }
            }
          }
        }
      } catch (_: Exception) {}

      cachedEspeakLexicon = map
      return map
    }
  }

  /**
   * Ensures the full 30,500+ word eSpeak-ng Arabic lexicon is loaded from Android [context] assets if not yet loaded.
   */
  fun ensureLexiconLoaded(context: android.content.Context) {
    val current = cachedEspeakLexicon
    if (current != null && current.size > 5000) return
    synchronized(lexiconLock) {
      val check = cachedEspeakLexicon
      if (check != null && check.size > 5000) return
      val map = HashMap<String, String>(32000)
      map.putAll(CORE_BUILTIN_ESPEAK_LEXICON)
      try {
        context.assets.open("tts/ar_espeak_lexicon.gz").use { raw ->
          java.util.zip.GZIPInputStream(raw).bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (line in lines) {
              val tabIdx = line.indexOf('\t')
              if (tabIdx > 0 && tabIdx < line.length - 1) {
                val word = line.substring(0, tabIdx)
                val mnem = line.substring(tabIdx + 1)
                if (!map.containsKey(word)) {
                  map[word] = mnem
                }
              }
            }
          }
        }
      } catch (_: Exception) {}
      cachedEspeakLexicon = map
    }
  }

  /**
   * Strips Arabic tashkeel/diacritics and tatweel to obtain the unvocalized lookup key for `ar_listx`.
   */
  private fun stripDiacritics(word: String): String {
    return buildString(word.length) {
      for (ch in word) {
        if (ch !in '\u064B'..'\u0652' && ch != '\u0670' && ch != '\u0640') {
          append(ch)
        }
      }
    }
  }

  private fun lookupStemMnemonic(stem: String, lexicon: Map<String, String>): String? {
    lexicon[stem]?.let { return it }
    val normHamza = stem.replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
    lexicon[normHamza]?.let { return it }
    if (stem.startsWith('ا') && stem.length >= 2) {
      lexicon["أ" + stem.substring(1)]?.let { return it }
      lexicon["إ" + stem.substring(1)]?.let { return it }
    }
    return null
  }

  private data class PrefixRule(val prefix: String, val moonMnem: String, val sunMnem: String?)

  private val MORPH_PREFIXES = listOf(
    PrefixRule("وال", "wal", "wa"),
    PrefixRule("بال", "bil", "bi"),
    PrefixRule("فال", "fal", "fa"),
    PrefixRule("كال", "kal", "ka"),
    PrefixRule("لل", "lil", "li"),
    PrefixRule("ال", "?al", "?a"),
    PrefixRule("وس", "wasa", null),
    PrefixRule("فست", "fasata", null),
    PrefixRule("ست", "sata", null),
    PrefixRule("سي", "saja", null),
    PrefixRule("سأ", "sa?a", null),
    PrefixRule("سن", "sana", null),
    PrefixRule("و", "wa", null),
    PrefixRule("ف", "fa", null),
    PrefixRule("ب", "bi", null),
    PrefixRule("ك", "ka", null),
    PrefixRule("ل", "li", null),
    PrefixRule("س", "sa", null)
  )

  private val MORPH_SUFFIXES = listOf(
    "كما" to "kuma:",
    "كم" to "kum",
    "كن" to "kun:a",
    "هما" to "huma:",
    "هم" to "hum",
    "هن" to "hun:a",
    "ها" to "ha:",
    "نا" to "na:",
    "ني" to "ni:",
    "تم" to "tum",
    "تين" to "tajni",
    "ات" to "a:t",
    "ون" to "u:na",
    "ين" to "i:na",
    "ان" to "a:ni",
    "ية" to "ij:a",
    "ة" to "a",
    "ه" to "hu",
    "ك" to "ka",
    "ي" to "i:",
    "ت" to "t",
    "ا" to "a:"
  )

  private fun applySunAssimilation(prefixMnem: String, stemMnem: String): String {
    // Double the initial consonant of stemMnem for sun-letter assimilation (e.g., ?a + Sawt -> ?aS:awt)
    val multiConsonants = listOf("dH", "gH", "s[", "t[", "dZ", "tS")
    for (mc in multiConsonants) {
      if (stemMnem.startsWith(mc)) {
        return prefixMnem + mc + ":" + stemMnem.substring(mc.length)
      }
      if (stemMnem.startsWith("'$mc")) {
        return prefixMnem + "'" + mc + ":" + stemMnem.substring(mc.length + 1)
      }
    }
    if (stemMnem.isNotEmpty()) {
      return if (stemMnem[0] == '\'' && stemMnem.length >= 2) {
        prefixMnem + "'" + stemMnem[1] + ":" + stemMnem.substring(2)
      } else {
        prefixMnem + stemMnem[0] + ":" + stemMnem.substring(1)
      }
    }
    return prefixMnem + stemMnem
  }

  /**
   * Resolves an Arabic word into an exact eSpeak-ng Arabic mnemonic string (using `ar_listx` + morphological affixes).
   */
  private fun resolveWordInEspeakLexicon(rawWord: String, lexicon: Map<String, String>): String? {
    val clean = stripDiacritics(rawWord)
    if (clean.isEmpty()) return null

    // 1. Direct lookup
    lookupStemMnemonic(clean, lexicon)?.let { return it }

    // 2. Prefix + stem (+ optional suffix)
    for (pRule in MORPH_PREFIXES) {
      val pref = pRule.prefix
      if (clean.startsWith(pref) && clean.length - pref.length >= 2) {
        val rem = clean.substring(pref.length)
        val isSun = pRule.sunMnem != null && rem[0] in SUN_ARABIC_CONSONANTS
        val prefMnem = if (isSun) pRule.sunMnem!! else pRule.moonMnem

        val directStem = lookupStemMnemonic(rem, lexicon)
        if (directStem != null) {
          return if (isSun) applySunAssimilation(prefMnem, directStem) else prefMnem + directStem
        }

        for ((suf, sufMnem) in MORPH_SUFFIXES) {
          if (rem.endsWith(suf) && rem.length - suf.length >= 2) {
            val base = rem.substring(0, rem.length - suf.length)
            for (cand in listOf(base, base + "ة", base + "ى")) {
              val baseMnem = lookupStemMnemonic(cand, lexicon)
              if (baseMnem != null) {
                val combinedStem = if (cand.endsWith("ة") && suf != "ة") {
                  if (baseMnem.endsWith("a") || baseMnem.endsWith("a.")) baseMnem + "t" + sufMnem else baseMnem + "at" + sufMnem
                } else {
                  baseMnem + sufMnem
                }
                return if (isSun) applySunAssimilation(prefMnem, combinedStem) else prefMnem + combinedStem
              }
            }
            if (base.endsWith("ت") && base.length >= 3) {
              val taaCand = base.substring(0, base.length - 1) + "ة"
              val baseMnem = lookupStemMnemonic(taaCand, lexicon)
              if (baseMnem != null) {
                val combinedStem = if (baseMnem.endsWith("a") || baseMnem.endsWith("a.")) {
                  baseMnem + "t" + sufMnem
                } else {
                  baseMnem + "at" + sufMnem
                }
                return if (isSun) applySunAssimilation(prefMnem, combinedStem) else prefMnem + combinedStem
              }
            }
          }
        }
      }
    }

    // 3. Suffix-only stripping on clean word
    for ((suf, sufMnem) in MORPH_SUFFIXES) {
      if (clean.endsWith(suf) && clean.length - suf.length >= 2) {
        val base = clean.substring(0, clean.length - suf.length)
        for (cand in listOf(base, base + "ة", base + "ى")) {
          val baseMnem = lookupStemMnemonic(cand, lexicon)
          if (baseMnem != null) {
            return if (cand.endsWith("ة") && suf != "ة") {
              if (baseMnem.endsWith("a") || baseMnem.endsWith("a.")) baseMnem + "t" + sufMnem else baseMnem + "at" + sufMnem
            } else {
              baseMnem + sufMnem
            }
          }
        }
        if (base.endsWith("ت") && base.length >= 3) {
          val taaCand = base.substring(0, base.length - 1) + "ة"
          val baseMnem = lookupStemMnemonic(taaCand, lexicon)
          if (baseMnem != null) {
            return if (baseMnem.endsWith("a") || baseMnem.endsWith("a.")) {
              baseMnem + "t" + sufMnem
            } else {
              baseMnem + "at" + sufMnem
            }
          }
        }
      }
    }

    return null
  }

  /**
   * Rule-based eSpeak-ng `ar_rules` grapheme-to-mnemonic converter for any novel Arabic word
   * not found in the 30,500-word lexicon.
   * Implements:
   * - Canonical diacritic reordering (`َّ` -> `َّ`)
   * - Definite article `ال` sun/moon assimilation
   * - Thick consonant (`ص ض ط ظ`) dark vowel backing (`a.`, `i.`, `u.`)
   * - Vowel+glide collapsing (`َا` -> `a:`, `ُو` -> `u:`, `ِي` -> `i:`, `َو` -> `aw`, `َي` -> `aj`)
   * - Epenthetic short vowel insertion for unvocalized consonant clusters so words are never vowel-starved.
   */
  private fun ruleBasedWordToEspeakMnemonic(word: String): String {
    // Canonical reorder: vowel + shadda -> shadda + vowel
    val canonical = word
      .replace("\u064E\u0651", "\u0651\u064E")
      .replace("\u064F\u0651", "\u0651\u064F")
      .replace("\u0650\u0651", "\u0651\u0650")

    val hasAnyShortVowels = canonical.any { it in SHORT_VOWELS }
    val sb = StringBuilder()
    var i = 0
    var prevWasThick = false

    // Handle leading definite article ال
    if (canonical.startsWith("ال") && canonical.length >= 3) {
      var afterLamIdx = 2
      while (afterLamIdx < canonical.length && canonical[afterLamIdx] in '\u064B'..'\u0652') {
        afterLamIdx++
      }
      val firstStemChar = if (afterLamIdx < canonical.length) canonical[afterLamIdx] else '\u0000'
      if (firstStemChar in SUN_ARABIC_CONSONANTS) {
        sb.append("?a")
        i = 2
      } else {
        sb.append("?al")
        i = 2
      }
    }

    while (i < canonical.length) {
      val ch = canonical[i]
      val next1 = if (i + 1 < canonical.length) canonical[i + 1] else '\u0000'
      val next2 = if (i + 2 < canonical.length) canonical[i + 2] else '\u0000'

      when (ch) {
        'آ' -> {
          sb.append("?a:")
          prevWasThick = false
          i++
          continue
        }
        'أ', 'إ', 'ء', 'ؤ', 'ئ' -> {
          sb.append("?")
          prevWasThick = false
        }
        'ا' -> {
          if (i == 0) {
            sb.append("?")
            if (!hasAnyShortVowels && next1 !in SHORT_VOWELS) {
              sb.append("a")
            }
          } else {
            sb.append(if (prevWasThick) "a.:" else "a:")
          }
          prevWasThick = false
          i++
          continue
        }
        'ى' -> {
          sb.append(if (prevWasThick) "a.:" else "a:")
          prevWasThick = false
          i++
          continue
        }
        'ب' -> { sb.append("b"); prevWasThick = false }
        'پ' -> { sb.append("p"); prevWasThick = false }
        'ت' -> { sb.append("t"); prevWasThick = false }
        'ة' -> {
          if (next1 in SHORT_VOWELS || next1 in setOf('\u064B', '\u064C', '\u064D')) {
            sb.append("t")
          } else {
            // Word-final taa marbuta in natural spoken Arabic
            if (!sb.endsWith("a") && !sb.endsWith("a.") && !sb.endsWith(":")) {
              sb.append(if (prevWasThick) "a." else "a")
            }
          }
          prevWasThick = false
          i++
          continue
        }
        'ث' -> { sb.append("T"); prevWasThick = false }
        'ج' -> { sb.append("dZ"); prevWasThick = false }
        'ح' -> { sb.append("H"); prevWasThick = false }
        'خ' -> { sb.append("X"); prevWasThick = false }
        'د' -> { sb.append("d"); prevWasThick = false }
        'ذ' -> { sb.append("D"); prevWasThick = false }
        'ر' -> { sb.append("R"); prevWasThick = false }
        'ز' -> { sb.append("z"); prevWasThick = false }
        'س' -> { sb.append("s"); prevWasThick = false }
        'ش' -> { sb.append("S"); prevWasThick = false }
        'ص' -> { sb.append("s["); prevWasThick = true }
        'ض' -> { sb.append("dH"); prevWasThick = true }
        'ط' -> { sb.append("t["); prevWasThick = true }
        'ظ' -> { sb.append("Z"); prevWasThick = true }
        'ع' -> { sb.append("A"); prevWasThick = false }
        'غ' -> { sb.append("Q"); prevWasThick = false }
        'ف' -> { sb.append("f"); prevWasThick = false }
        'ق' -> { sb.append("q"); prevWasThick = false }
        'ك' -> { sb.append("k"); prevWasThick = false }
        'گ' -> { sb.append("g"); prevWasThick = false }
        'ل' -> { sb.append("l"); prevWasThick = false }
        'م' -> { sb.append("m"); prevWasThick = false }
        'ن' -> { sb.append("n"); prevWasThick = false }
        'ه' -> { sb.append("h"); prevWasThick = false }
        'و' -> {
          if (i == 0 || next1 in SHORT_VOWELS || next1 == '\u0651' || next1 == 'ا') {
            sb.append("w")
          } else {
            sb.append(if (prevWasThick) "u.:" else "u:")
            prevWasThick = false
            i++
            continue
          }
          prevWasThick = false
        }
        'ي' -> {
          if (i == 0 || next1 in SHORT_VOWELS || next1 == '\u0651' || next1 == 'ا') {
            sb.append("j")
          } else {
            sb.append(if (prevWasThick) "i.:" else "i:")
            prevWasThick = false
            i++
            continue
          }
          prevWasThick = false
        }
        '\u0651' -> {
          sb.append(":")
          i++
          continue
        }
        '\u064E' -> {
          // Fatha: check if followed by ا / ى / وْ / يْ
          if (next1 == 'ا' || next1 == 'ى') {
            sb.append(if (prevWasThick) "a.:" else "a:")
            prevWasThick = false
            i += 2
            continue
          } else if (next1 == 'و' && next2 == '\u0652') {
            sb.append(if (prevWasThick) "a.w" else "aw")
            prevWasThick = false
            i += 3
            continue
          } else if (next1 == 'ي' && next2 == '\u0652') {
            sb.append(if (prevWasThick) "a.j" else "aj")
            prevWasThick = false
            i += 3
            continue
          } else {
            sb.append(if (prevWasThick) "a." else "a")
            prevWasThick = false
            i++
            continue
          }
        }
        '\u064F' -> {
          if (next1 == 'و' && next2 !in SHORT_VOWELS && next2 != '\u0651') {
            sb.append(if (prevWasThick) "u.:" else "u:")
            prevWasThick = false
            i += 2
            continue
          } else {
            sb.append(if (prevWasThick) "u." else "u")
            prevWasThick = false
            i++
            continue
          }
        }
        '\u0650' -> {
          if (next1 == 'ي' && next2 !in SHORT_VOWELS && next2 != '\u0651') {
            sb.append(if (prevWasThick) "i.:" else "i:")
            prevWasThick = false
            i += 2
            continue
          } else {
            sb.append(if (prevWasThick) "i." else "i")
            prevWasThick = false
            i++
            continue
          }
        }
        '\u064B' -> {
          sb.append(if (prevWasThick) "a.n" else "an")
          prevWasThick = false
          if (next1 == 'ا' || next1 == 'ى') i += 2 else i++
          continue
        }
        '\u064C' -> {
          sb.append(if (prevWasThick) "u.n" else "un")
          prevWasThick = false
          i++
          continue
        }
        '\u064D' -> {
          sb.append(if (prevWasThick) "i.n" else "in")
          prevWasThick = false
          i++
          continue
        }
        '\u0670' -> {
          sb.append(if (prevWasThick) "a.:" else "a:")
          prevWasThick = false
          i++
          continue
        }
        '\u0652' -> {
          prevWasThick = false
          i++
          continue
        }
        in 'a'..'z', in 'A'..'Z' -> {
          sb.append(ch.lowercaseChar())
          prevWasThick = false
          i++
          continue
        }
        else -> {
          i++
          continue
        }
      }

      // If the word has no short vowels written and this consonant is immediately followed by another true consonant
      // (not a long vowel ا / و / ي / ى and not word-final), insert natural epenthetic fatha ('a' or 'a.')
      // just like Arabic CV syllable structure requires.
      if (!hasAnyShortVowels && i < canonical.length - 1) {
        val peek = canonical[i + 1]
        if (peek in '\u0621'..'\u064A' && peek !in setOf('ا', 'و', 'ي', 'ى')) {
          sb.append(if (prevWasThick) "a." else "a")
          prevWasThick = false
        }
      }

      i++
    }

    return sb.toString()
  }

  /**
   * Translates an eSpeak-ng Arabic mnemonic string (from `ar_listx` / `ar_rules`) into the exact
   * IPA Unicode symbols expected by Piper VITS ONNX `phoneme_id_map`:
   * - `s[` -> `s` + `\u032A` (voiceless dental/emphatic s)
   * - `t[` -> `t` + `\u032A` (voiceless dental/emphatic t)
   * - `dH` -> `d` + `\u02E4` (`dˤ`)
   * - `Z`  -> `ð` (`\u00F0`)
   * - `X`  -> `χ` (`\u03C7`)
   * - `Q` / `gH` -> `ɣ` (`\u0263`)
   * - `A`  -> `ʕ` (`\u0295`)
   * - `H`  -> `ħ` (`\u0127`)
   * - `T`  -> `θ` (`\u03B8`)
   * - `D`  -> `ð` (`\u00F0`)
   * - `S`  -> `ʃ` (`\u0283`)
   * - `dZ` -> `d` + `ʒ` (`\u0292`)
   * - `R`  -> `r`
   * - `?`  -> `ʔ` (`\u0294`)
   * - `a.`, `i.`, `u.` -> `a` + `.`, `i` + `.`, `u` + `.` (dark vowels after thick consonants)
   * - `:`  -> `ː` (`\u02D0`)
   * - `'`  -> `ˈ` (`\u02C8` primary stress)
   * - `,`  -> `ˌ` (`\u02CC` secondary stress)
   */
  private fun espeakMnemonicToIpaTokens(mnem: String): List<String> {
    val out = ArrayList<String>(mnem.length + 4)
    var i = 0
    while (i < mnem.length) {
      when {
        mnem.startsWith("dH", i) -> { out.add("d"); out.add("\u02E4"); i += 2 }
        mnem.startsWith("gH", i) -> { out.add("\u0263"); i += 2 }
        mnem.startsWith("s[", i) -> { out.add("s"); out.add("\u032A"); i += 2 }
        mnem.startsWith("t[", i) -> { out.add("t"); out.add("\u032A"); i += 2 }
        mnem.startsWith("dZ", i) -> { out.add("d"); out.add("\u0292"); i += 2 }
        mnem.startsWith("tS", i) -> { out.add("t"); out.add("\u0283"); i += 2 }
        mnem.startsWith("a.", i) -> { out.add("a"); out.add("."); i += 2 }
        mnem.startsWith("i.", i) -> { out.add("i"); out.add("."); i += 2 }
        mnem.startsWith("u.", i) -> { out.add("u"); out.add("."); i += 2 }
        else -> {
          when (val c = mnem[i]) {
            '?' -> out.add("\u0294")
            'A' -> out.add("\u0295")
            'H' -> out.add("\u0127")
            'X' -> out.add("\u03C7")
            'Q' -> out.add("\u0263")
            'T' -> out.add("\u03B8")
            'D' -> out.add("\u00F0")
            'Z' -> out.add("\u00F0")
            'S' -> out.add("\u0283")
            'R' -> out.add("r")
            ':' -> out.add("\u02D0")
            '\'' -> out.add("\u02C8")
            ',' -> out.add("\u02CC")
            '%', '=', '-', '_', '|' -> { /* virtual eSpeak flags not emitted in IPA */ }
            ' ' -> out.add(" ")
            else -> out.add(c.toString())
          }
          i++
        }
      }
    }
    return out
  }

  /**
   * Converts Arabic text (unvocalized or vocalized, with optional embedded numbers/symbols) into an
   * authentic eSpeak-ng (`voice: ar`) IPA phoneme sequence matching Piper VITS ONNX `phoneme_id_map`.
   */
  fun arabicToIpaPhonemes(vocalizedText: String): List<String> {
    val prepared = prepareAndDiacritize(vocalizedText)
    if (prepared.isBlank()) return emptyList()

    val lexicon = getEspeakLexicon()
    val tokens = mutableListOf<String>()
    val rawTokens = prepared.trim().split(Regex("\\s+"))

    for (rawTok in rawTokens) {
      if (rawTok.isEmpty()) continue

      if (rawTok == "،" || rawTok == ",") {
        tokens.add(",")
        tokens.add(" ")
        continue
      }
      if (rawTok == "." || rawTok == "!" || rawTok == "?" || rawTok == "؟") {
        tokens.add(if (rawTok == "؟" || rawTok == "?") "?" else if (rawTok == "!") "!" else ".")
        continue
      }

      // Separate leading/trailing punctuation from the word core
      var startIdx = 0
      while (startIdx < rawTok.length && !rawTok[startIdx].isLetterOrDigit() && rawTok[startIdx] !in '\u064B'..'\u0652') {
        startIdx++
      }
      var endIdx = rawTok.length
      while (endIdx > startIdx && !rawTok[endIdx - 1].isLetterOrDigit() && rawTok[endIdx - 1] !in '\u064B'..'\u0652') {
        endIdx--
      }

      val wordCore = rawTok.substring(startIdx, endIdx)
      val trailingPunct = rawTok.substring(endIdx)

      if (wordCore.isNotEmpty()) {
        if (tokens.isNotEmpty() && tokens.last() != " ") {
          tokens.add(" ")
        }
        val espeakMnem = resolveWordInEspeakLexicon(wordCore, lexicon)
          ?: ruleBasedWordToEspeakMnemonic(wordCore)
        tokens.addAll(espeakMnemonicToIpaTokens(espeakMnem))
      }

      if (trailingPunct.isNotEmpty()) {
        for (p in trailingPunct) {
          when (p) {
            '،', ',' -> tokens.add(",")
            '؛', ';' -> tokens.add(";")
            ':', '：' -> tokens.add(":")
            '؟', '?' -> tokens.add("?")
            '!' -> tokens.add("!")
            '.' -> tokens.add(".")
          }
        }
      }
    }

    while (tokens.isNotEmpty() && tokens.last() == " ") {
      tokens.removeAt(tokens.lastIndex)
    }
    return tokens
  }
}
