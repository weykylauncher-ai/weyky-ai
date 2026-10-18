package com.example.core.intent.handlers

import com.example.core.intent.ExecutionResult
import com.example.device.memory.MemoryProvider
import com.example.device.storage.StorageProvider
import com.example.rendering.visual_state.CoreVisualState
import java.util.Locale

class StorageMemoryActionHandler(
  private val memoryProvider: MemoryProvider,
  private val storageProvider: StorageProvider
) {

  fun handleRam(): ExecutionResult {
    val m = memoryProvider.queryMemory()
      ?: return ExecutionResult.Unavailable("بيانات الذاكرة العشوائية غير متوفرة.")

    val isArabic = Locale.getDefault().language.startsWith("ar")
    val text = if (isArabic) {
      "الذاكرة العشوائية (RAM) مستخدمة بنسبة ${m.usagePercent}% (${m.usedRamFormatted} من أصل ${m.totalRamFormatted}). المتاح حالياً ${m.availableRamFormatted}."
    } else {
      "RAM is ${m.usagePercent}% used (${m.usedRamFormatted} of ${m.totalRamFormatted}). ${m.availableRamFormatted} available."
    }

    return ExecutionResult.Success(
      speech = text,
      actionTaken = "Queried RAM (${m.usagePercent}%)",
      visualState = CoreVisualState.RESPONDING
    )
  }

  fun handleStorage(): ExecutionResult {
    val s = storageProvider.queryStorage()
      ?: return ExecutionResult.Unavailable("بيانات مساحة التخزين غير متوفرة.")

    val isArabic = Locale.getDefault().language.startsWith("ar")
    val text = if (isArabic) {
      "مساحة التخزين مستخدمة بنسبة ${s.usagePercent}%. المتبقي ${s.freeFormatted} من أصل ${s.totalFormatted}."
    } else {
      "Internal storage is ${s.usagePercent}% used. ${s.freeFormatted} free of ${s.totalFormatted}."
    }

    return ExecutionResult.Success(
      speech = text,
      actionTaken = "Queried Storage (${s.freeFormatted} free)",
      visualState = CoreVisualState.RESPONDING
    )
  }
}
