package com.example.core.voice.engine

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import java.util.Locale

/**
 * Microsoft Edge Neural TTS DRM Security Token Generator (Sec-MS-GEC v2).
 * Implements the exact proprietary CS64 + SHA-256 schedule algorithm required by Microsoft's
 * Read Aloud / Bing Speech endpoints to eliminate HTTP 403 Forbidden errors and allow authentic,
 * ultra-natural studio voices (Salma, Hamed, Shakir, Fatima, Zariyah, etc.) to synthesize seamlessly.
 */
object EdgeDrmTokenGenerator {

  const val TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
  const val CHROMIUM_FULL_VERSION = "143.0.3650.75"
  const val SEC_MS_GEC_VERSION = "2-$CHROMIUM_FULL_VERSION"
  private const val WIN_EPOCH = 11644473600L
  private const val S_TO_NS = 1_000_000_000.0

  private val SHA256_INITIAL = intArrayOf(
    0x6A09E667, 0xBB67AE85.toInt(), 0x3C6EF372, 0xA54FF53A.toInt(),
    0x510E527F, 0x9B05688C.toInt(), 0x1F83D9AB, 0x5BE0CD19
  )

  private val SHA256_STATE = intArrayOf(
    0xFD9FBEA2.toInt(), 0x91243CE7.toInt(), 0xBE499094.toInt(), 0xCD2E1E0A.toInt(),
    0x6C006F49, 0x04BD35AB, 0xFEB0A7E4.toInt(), 0x03EFFBD2
  )

  private val SHA256_SCHEDULE = intArrayOf(
    0xEB24BF9E.toInt(), 0x1558691F, 0xA0C09EE2.toInt(), 0x0CFA32BA,
    0xA622EE8C.toInt(), 0xD1DD9491.toInt(), 0xC0DD25A0.toInt(), 0xF9E70AB6.toInt(),
    0xF0C31573.toInt(), 0x7058CE4E, 0x7188E606, 0x00E5AB33,
    0xD258B060.toInt(), 0x72766564
  )

  private val SHA256_ROUND_CONSTANTS = intArrayOf(
    0x428A2F98, 0x71374491, 0xB5C0FBCF.toInt(), 0xE9B5DBA5.toInt(),
    0x3956C25B, 0x59F111F1, 0x923F82A4.toInt(), 0xAB1C5ED5.toInt(),
    0xD807AA98.toInt(), 0x12835B01, 0x243185BE, 0x550C7DC3,
    0x72BE5D74, 0x80DEB1FE.toInt(), 0x9BDC06A7.toInt(), 0xC19BF174.toInt(),
    0xE49B69C1.toInt(), 0xEFBE4786.toInt(), 0x0FC19DC6, 0x240CA1CC,
    0x2DE92C6F, 0x4A7484AA, 0x5CB0A9DC, 0x76F988DA,
    0x983E5152.toInt(), 0xA831C66D.toInt(), 0xB00327C8.toInt(), 0xBF597FC7.toInt(),
    0xC6E00BF3.toInt(), 0xD5A79147.toInt(), 0x06CA6351, 0x14292967,
    0x27B70A85, 0x2E1B2138, 0x4D2C6DFC, 0x53380D13,
    0x650A7354, 0x766A0ABB, 0x81C2C92E.toInt(), 0x92722C85.toInt(),
    0xA2BFE8A1.toInt(), 0xA81A664B.toInt(), 0xC24B8B70.toInt(), 0xC76C51A3.toInt(),
    0xD192E819.toInt(), 0xD6990624.toInt(), 0xF40E3585.toInt(), 0x106AA070,
    0x19A4C116, 0x1E376C08, 0x2748774C, 0x34B0BCB5,
    0x391C0CB3, 0x4ED8AA4A, 0x5B9CCA4F, 0x682E6FF3,
    0x748F82EE, 0x78A5636F, 0x84C87814.toInt(), 0x8CC70208.toInt(),
    0x90BEFFFA.toInt(), 0xA4506CEB.toInt(), 0xBEF9A3F7.toInt(), 0xC67178F2.toInt()
  )

  private const val SHA256_ADJUSTMENT = 0xC9DC06A7.toInt()

  private val SHA256_BLOCK_CONSTANTS = IntArray(64).apply {
    System.arraycopy(SHA256_ROUND_CONSTANTS, 0, this, 0, 14)
    this[14] = SHA256_ADJUSTMENT
    System.arraycopy(SHA256_ROUND_CONSTANTS, 15, this, 15, 49)
  }

  private const val SHA256_SIGMA0_ADJUSTMENT = 0x059C0B80
  private const val SHA256_SIGMA1_ADJUSTMENT = 0x000B92C0

  private val CS64_COEFFICIENTS = arrayOf(
    Pair(0xE82E91B1.toInt(), 0xB32700D0.toInt()),
    Pair(0x44DE24C6, 0x88DFF51D.toInt()),
    Pair(0x3BEB65BC, 0x29514038),
    Pair(0xC89C5933.toInt(), 0xB328CBDF.toInt()),
    Pair(0x8D1EC211.toInt(), 0xD405B4D5.toInt()),
    Pair(0x314EF5D5, 0x26E55F3C),
    Pair(0xE92491B1.toInt(), 0x607801C6),
    Pair(0xF0A5B9D5.toInt(), 0x82B2C1FB.toInt()),
    Pair(0x06064CF3, 0xFA51A3DA.toInt()),
    Pair(0xDA79FA60.toInt(), 0x207CC464),
    Pair(0xBED20560.toInt(), 0x3C3C74F3),
    Pair(0x89F9C9F8.toInt(), 0xD2D14B61.toInt()),
    Pair(0x35FA38C6, 0xBB04A2C1.toInt()),
    Pair(0xCB49E3C6.toInt(), 0xC5626167.toInt())
  )

  private const val CS64_FACTOR = 0x82893933.toInt()
  private const val CS64_ADJUSTMENT0 = 0xF2F90B1A.toInt()
  private const val CS64_ADJUSTMENT1 = 0x0B1A0000

  @Volatile
  var clockSkewSeconds: Double = 0.0

  private val secureRandom = SecureRandom()

  fun generateMuid(): String {
    val bytes = ByteArray(16)
    secureRandom.nextBytes(bytes)
    return bytes.joinToString("") { "%02X".format(it) }
  }

  /**
   * Generates the Sec-MS-GEC v2 token combining CS64 and custom SHA256 digests.
   */
  fun generateSecMsGec(unixTimestamp: Double? = null): String {
    val currentTs = unixTimestamp ?: ((System.currentTimeMillis() / 1000.0) + clockSkewSeconds)
    var ticks = currentTs + WIN_EPOCH
    ticks -= (ticks % 300.0)
    ticks *= (S_TO_NS / 100.0)
    val strToHash = String.format(Locale.US, "%.0f", ticks) + TRUSTED_CLIENT_TOKEN
    val data = strToHash.toByteArray(Charsets.US_ASCII)

    val digest = sha256Digest(data)
    val (csA, csB) = cs64Digest(data, digest)

    val cs64Hex = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).apply {
      putInt(csA)
      putInt(csB)
    }.array().joinToString("") { "%02X".format(it) }

    val sha256Hex = digest.joinToString("") { "%02X".format(it) }

    return "$cs64Hex-$sha256Hex"
  }

  private fun rol16(v: Int): Int {
    return (v shl 16) or (v ushr 16)
  }

  private fun sigma0(v: Int): Int {
    return (v ushr 7) xor (v shl 25) xor (v ushr 18) xor (v shl 14) xor (v ushr 3)
  }

  private fun sigma1(v: Int): Int {
    return (v ushr 17) xor (v shl 15) xor (v ushr 19) xor (v shl 13) xor (v ushr 10)
  }

  private fun sha256Rounds(
    state: IntArray,
    words: IntArray,
    firstRound: Int = 0
  ): IntArray {
    var a = state[0]
    var b = state[1]
    var c = state[2]
    var d = state[3]
    var e = state[4]
    var f = state[5]
    var g = state[6]
    var h = state[7]

    val constants = if (firstRound == 14) SHA256_BLOCK_CONSTANTS else SHA256_ROUND_CONSTANTS
    val aligned = (firstRound + 7) / 8 * 8

    for (index in firstRound until aligned) {
      val eL = e.toLong() and 0xFFFFFFFFL
      val aL = a.toLong() and 0xFFFFFFFFL
      val rotE = eL * 0x100000001L
      val rotA = aL * 0x100000001L

      val t1 = (h + (((rotE ushr 6) xor (rotE ushr 11) xor (rotE ushr 25)).toInt()) +
        (g xor (e and (f xor g))) + constants[index] + words[index])
      val t2 = (((rotA ushr 2) xor (rotA ushr 13) xor (rotA ushr 22)).toInt()) + ((a and b) or (c and (a or b)))

      val nextA = t1 + t2
      val nextD = d + t1
      h = g
      g = f
      f = e
      e = nextD
      d = c
      c = b
      b = a
      a = nextA
    }

    var index = aligned
    while (index < 64) {
      // Round 0
      run {
        val eL = e.toLong() and 0xFFFFFFFFL
        val aL = a.toLong() and 0xFFFFFFFFL
        val rotE = eL * 0x100000001L
        val rotA = aL * 0x100000001L
        val t1 = (h + (((rotE ushr 6) xor (rotE ushr 11) xor (rotE ushr 25)).toInt()) +
          (g xor (e and (f xor g))) + constants[index] + words[index])
        d += t1
        h = (t1 + (((rotA ushr 2) xor (rotA ushr 13) xor (rotA ushr 22)).toInt()) + ((a and b) or (c and (a or b))))
      }
      // Round 1
      run {
        val eL = d.toLong() and 0xFFFFFFFFL
        val aL = h.toLong() and 0xFFFFFFFFL
        val rotE = eL * 0x100000001L
        val rotA = aL * 0x100000001L
        val t1 = (g + (((rotE ushr 6) xor (rotE ushr 11) xor (rotE ushr 25)).toInt()) +
          (f xor (d and (e xor f))) + constants[index + 1] + words[index + 1])
        c += t1
        g = (t1 + (((rotA ushr 2) xor (rotA ushr 13) xor (rotA ushr 22)).toInt()) + ((h and a) or (b and (h or a))))
      }
      // Round 2
      run {
        val eL = c.toLong() and 0xFFFFFFFFL
        val aL = g.toLong() and 0xFFFFFFFFL
        val rotE = eL * 0x100000001L
        val rotA = aL * 0x100000001L
        val t1 = (f + (((rotE ushr 6) xor (rotE ushr 11) xor (rotE ushr 25)).toInt()) +
          (e xor (c and (d xor e))) + constants[index + 2] + words[index + 2])
        b += t1
        f = (t1 + (((rotA ushr 2) xor (rotA ushr 13) xor (rotA ushr 22)).toInt()) + ((g and h) or (a and (g or h))))
      }
      // Round 3
      run {
        val eL = b.toLong() and 0xFFFFFFFFL
        val aL = f.toLong() and 0xFFFFFFFFL
        val rotE = eL * 0x100000001L
        val rotA = aL * 0x100000001L
        val t1 = (e + (((rotE ushr 6) xor (rotE ushr 11) xor (rotE ushr 25)).toInt()) +
          (d xor (b and (c xor d))) + constants[index + 3] + words[index + 3])
        a += t1
        e = (t1 + (((rotA ushr 2) xor (rotA ushr 13) xor (rotA ushr 22)).toInt()) + ((f and g) or (h and (f or g))))
      }
      // Round 4
      run {
        val eL = a.toLong() and 0xFFFFFFFFL
        val aL = e.toLong() and 0xFFFFFFFFL
        val rotE = eL * 0x100000001L
        val rotA = aL * 0x100000001L
        val t1 = (d + (((rotE ushr 6) xor (rotE ushr 11) xor (rotE ushr 25)).toInt()) +
          (c xor (a and (b xor c))) + constants[index + 4] + words[index + 4])
        h += t1
        d = (t1 + (((rotA ushr 2) xor (rotA ushr 13) xor (rotA ushr 22)).toInt()) + ((e and f) or (g and (e or f))))
      }
      // Round 5
      run {
        val eL = h.toLong() and 0xFFFFFFFFL
        val aL = d.toLong() and 0xFFFFFFFFL
        val rotE = eL * 0x100000001L
        val rotA = aL * 0x100000001L
        val t1 = (c + (((rotE ushr 6) xor (rotE ushr 11) xor (rotE ushr 25)).toInt()) +
          (b xor (h and (a xor b))) + constants[index + 5] + words[index + 5])
        g += t1
        c = (t1 + (((rotA ushr 2) xor (rotA ushr 13) xor (rotA ushr 22)).toInt()) + ((d and e) or (f and (d or e))))
      }
      // Round 6
      run {
        val eL = g.toLong() and 0xFFFFFFFFL
        val aL = c.toLong() and 0xFFFFFFFFL
        val rotE = eL * 0x100000001L
        val rotA = aL * 0x100000001L
        val t1 = (b + (((rotE ushr 6) xor (rotE ushr 11) xor (rotE ushr 25)).toInt()) +
          (a xor (g and (h xor a))) + constants[index + 6] + words[index + 6])
        f += t1
        b = (t1 + (((rotA ushr 2) xor (rotA ushr 13) xor (rotA ushr 22)).toInt()) + ((c and d) or (e and (c or d))))
      }
      // Round 7
      run {
        val eL = f.toLong() and 0xFFFFFFFFL
        val aL = b.toLong() and 0xFFFFFFFFL
        val rotE = eL * 0x100000001L
        val rotA = aL * 0x100000001L
        val t1 = (a + (((rotE ushr 6) xor (rotE ushr 11) xor (rotE ushr 25)).toInt()) +
          (h xor (f and (g xor h))) + constants[index + 7] + words[index + 7])
        e += t1
        a = (t1 + (((rotA ushr 2) xor (rotA ushr 13) xor (rotA ushr 22)).toInt()) + ((b and c) or (d and (b or c))))
      }
      index += 8
    }

    return intArrayOf(a, b, c, d, e, f, g, h)
  }

  private fun expandSha256Schedule(words: IntArray, firstWord: Int = 16) {
    for (index in firstWord until 64) {
      val x = words[index - 2]
      val y = words[index - 15]
      val sig1 = (x ushr 17) xor (x shl 15) xor (x ushr 19) xor (x shl 13) xor (x ushr 10)
      val sig0 = (y ushr 7) xor (y shl 25) xor (y ushr 18) xor (y shl 14) xor (y ushr 3)
      words[index] = sig1 + words[index - 7] + sig0 + words[index - 16]
    }
  }

  private fun sha256Init(data: ByteArray): IntArray {
    val words = IntArray(64)
    // words[14] = int.from_bytes(data[:3], "big")
    val b0 = data[0].toInt() and 0xFF
    val b1 = data[1].toInt() and 0xFF
    val b2 = data[2].toInt() and 0xFF
    words[14] = (b0 shl 16) or (b1 shl 8) or b2

    // words[15] = int.from_bytes(data[3:7], "big")
    val b3 = data[3].toInt() and 0xFF
    val b4 = data[4].toInt() and 0xFF
    val b5 = data[5].toInt() and 0xFF
    val b6 = data[6].toInt() and 0xFF
    words[15] = (b3 shl 24) or (b4 shl 16) or (b5 shl 8) or b6

    for (index in 16 until 30) {
      var value = SHA256_SCHEDULE[index - 16]
      if (index - 2 >= 14) {
        val s1 = sigma1(words[index - 2])
        value += if (index == 16) (s1 xor SHA256_SIGMA1_ADJUSTMENT) else s1
      }
      if (index - 7 >= 14) {
        value += words[index - 7]
      }
      if (index - 15 >= 14) {
        val s0 = sigma0(words[index - 15])
        value += if (index == 29) (s0 xor SHA256_SIGMA0_ADJUSTMENT) else s0
      }
      words[index] = value
    }

    words[30] = (
      sigma1(words[28]) + words[23] + sigma0(words[15]) + words[14] +
        SHA256_ADJUSTMENT - SHA256_ROUND_CONSTANTS[14]
    )

    expandSha256Schedule(words, 31)
    val working = sha256Rounds(SHA256_STATE, words, 14)
    return IntArray(8) { i -> SHA256_INITIAL[i] + working[i] }
  }

  private fun sha256Digest(data: ByteArray): ByteArray {
    require(data.size >= 7) { "Token salt must contain at least seven bytes" }
    val length = 57 + data.size
    val padZeroCount = ((55 - length) % 64 + 64) % 64
    val paddedSize = (data.size - 7) + 1 + padZeroCount + 8
    val padded = ByteArray(paddedSize)

    System.arraycopy(data, 7, padded, 0, data.size - 7)
    padded[data.size - 7] = 0x80.toByte()

    // 8 bytes big endian length in bits
    val lengthBits = length.toLong() * 8L
    ByteBuffer.wrap(padded, padded.size - 8, 8).order(ByteOrder.BIG_ENDIAN).putLong(lengthBits)

    var state = sha256Init(data)

    var offset = 0
    val words = IntArray(64)
    val byteBuffer = ByteBuffer.wrap(padded).order(ByteOrder.BIG_ENDIAN)

    while (offset < padded.size) {
      byteBuffer.position(offset)
      for (i in 0 until 16) {
        words[i] = byteBuffer.getInt()
      }
      expandSha256Schedule(words, 16)
      val working = sha256Rounds(state, words)
      state = IntArray(8) { i -> state[i] + working[i] }
      offset += 64
    }

    val out = ByteArray(32)
    val outBuffer = ByteBuffer.wrap(out).order(ByteOrder.BIG_ENDIAN)
    for (s in state) {
      outBuffer.putInt(s)
    }
    return out
  }

  private fun cs64Digest(data: ByteArray, digest: ByteArray): Pair<Int, Int> {
    val digestBuf = ByteBuffer.wrap(digest).order(ByteOrder.LITTLE_ENDIAN)
    var keyA = digestBuf.getInt() or 1
    var keyB = digestBuf.getInt() or 1

    val factor0 = keyA * CS64_FACTOR
    val factor1 = keyB * CS64_FACTOR

    val terms = ArrayList<Int>(64)
    val offsets = ArrayList<Int>(64)

    for (index in CS64_COEFFICIENTS.indices) {
      val coeff = CS64_COEFFICIENTS[index].first
      val off = CS64_COEFFICIENTS[index].second
      val fact = if (index % 2 == 0) factor0 else factor1
      terms.add(coeff * fact)
      offsets.add(off)
    }

    // _words_le4_even(b"\x00" + data)
    val prep = ByteArray(1 + data.size)
    prep[0] = 0.toByte()
    System.arraycopy(data, 0, prep, 1, data.size)

    val padLen = ((-prep.size) % 4 + 4) % 4
    val padded = ByteArray(prep.size + padLen)
    System.arraycopy(prep, 0, padded, 0, prep.size)

    val wordCount = padded.size / 4
    val wordsList = ArrayList<Int>(wordCount + 1)
    val pBuf = ByteBuffer.wrap(padded).order(ByteOrder.LITTLE_ENDIAN)
    for (i in 0 until wordCount) {
      wordsList.add(pBuf.getInt())
    }
    if (wordsList.size % 2 != 0) {
      wordsList.add(0)
    }

    for (index in wordsList.indices) {
      val word = wordsList[index]
      val isEven = (index % 2 == 0)
      var term = word * (if (isEven) keyA else keyB)
      var off = rol16(word) * (if (isEven) 0xEF0569FB.toInt() else 0xC31713DB.toInt())
      if (index == 0) {
        term += CS64_ADJUSTMENT0 * factor0
        off += CS64_ADJUSTMENT1
      }
      terms.add(term)
      offsets.add(off)
    }

    var swapAcc = 0
    var swapLast = 0
    var state = 0
    var acc = 0

    var index = 0
    while (index < terms.size) {
      val x = terms[index]
      val y = terms[index + 1]

      var t = rol16(offsets[index] + x)
      var q = t * 0x76F50F34.toInt()
      swapAcc += (rol16(q) * 0xC3EFEA97.toInt() + q)

      t = rol16(offsets[index + 1] + y)
      q = t * 0x1881CE3C
      swapLast = (rol16(q) * 0x35BD1EC9 + q)
      swapAcc += swapLast

      state = state * keyA + x
      state = rol16(state) * 0xCF98B111.toInt()
      state = rol16(state) * 0x87085B9F.toInt()
      state = rol16(state) * 0x12CEB96D
      state = rol16(state) * 0x257E1D83
      acc += state

      t = state * keyB + y
      t = rol16(t) * 0xA27416F5.toInt()
      t = rol16(t) * 0xD38396FF.toInt()
      state = rol16(rol16(t) * 0x7C932B89) * 0xBFA49F69.toInt()
      acc += state

      index += 2
    }

    return Pair(swapLast xor state, swapAcc xor acc)
  }
}
