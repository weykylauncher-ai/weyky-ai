package com.example.core.intent.handlers

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.example.core.intent.CallQueryType
import com.example.core.intent.ExecutionResult
import com.example.core.intent.WeykyIntent
import com.example.device.calls.CallLogProvider
import com.example.device.calls.CallType
import com.example.device.calls.ContactLookupProvider
import com.example.device.calls.ContactLookupResult
import com.example.rendering.visual_state.CoreVisualState
import java.util.Locale

class CallActionHandler(
  private val context: Context,
  private val callLogProvider: CallLogProvider,
  private val contactLookupProvider: ContactLookupProvider
) {

  fun handle(intent: WeykyIntent.CallIntent): ExecutionResult {
    val isArabic = Locale.getDefault().language.startsWith("ar")

    // Handle query types
    when (intent.queryType) {
      CallQueryType.MISSED -> return handleMissedCallsQuery(isArabic)
      CallQueryType.OUTGOING -> return handleLatestOutgoingQuery(isArabic)
      CallQueryType.INCOMING -> return handleLatestIncomingQuery(isArabic)
      CallQueryType.ALL -> return handleAllRecentCallsQuery(isArabic)
      CallQueryType.DIAL_TARGET -> {
        if (intent.showMissed) {
          return handleMissedCallsQuery(isArabic)
        }
      }
    }

    // Direct call target specified
    val target = intent.target
    if (target.isNullOrBlank()) {
      return ExecutionResult.Fallback(
        explanation = if (isArabic) "يرجى تحديد اسم جهة الاتصال أو الرقم المطلوب الاتصال به." else "Please specify a contact name or number to call."
      )
    }

    // Check if target is digits
    val isPhoneNumber = target.all { it.isDigit() || it == '+' || it == '-' || it == ' ' } && target.filter { it.isDigit() }.length >= 3
    if (isPhoneNumber) {
      return placeOrDialCall(target, null, isArabic)
    }

    if (!contactLookupProvider.hasContactsPermission()) {
      return ExecutionResult.RequiresPermission(
        permission = Manifest.permission.READ_CONTACTS,
        rationale = if (isArabic) "يلزم منح صلاحية جهات الاتصال للبحث عن '$target'." else "Permission to access contacts is required to look up '${target}'."
      )
    }

    val lookupResult = contactLookupProvider.lookupContact(target)
    return when (lookupResult) {
      is ContactLookupResult.PermissionRequired -> {
        ExecutionResult.RequiresPermission(
          permission = Manifest.permission.READ_CONTACTS,
          rationale = if (isArabic) "يلزم منح صلاحية جهات الاتصال للبحث عن '$target'." else "Permission to access contacts is required to look up '${target}'."
        )
      }
      is ContactLookupResult.NotFound -> {
        ExecutionResult.Error(
          friendlyMessage = if (isArabic) "لم يتم العثور على جهة اتصال باسم '$target' في هاتفك." else "No contact found matching '${target}' in your address book.",
          visualState = CoreVisualState.ERROR
        )
      }
      is ContactLookupResult.FoundSingle -> {
        placeOrDialCall(lookupResult.contact.phoneNumber, lookupResult.contact.displayName, isArabic)
      }
      is ContactLookupResult.FoundMultiple -> {
        val options = lookupResult.contacts.map { "${it.displayName}: ${it.phoneNumber}" }
        val prompt = if (isArabic) {
          "وجدت ${lookupResult.contacts.size} جهات اتصال باسم '$target'. أيهم تريد الاتصال به؟"
        } else {
          "Found ${lookupResult.contacts.size} contacts named '${target}'. Which one would you like to call?"
        }
        ExecutionResult.NeedsConfirmation(
          prompt = prompt,
          options = options,
          onConfirmOption = { selected ->
            val number = selected.substringAfterLast(":").trim()
            val name = selected.substringBefore(":").trim()
            placeOrDialCall(number, name, isArabic)
          },
          visualState = CoreVisualState.CALL
        )
      }
    }
  }

  private fun handleMissedCallsQuery(isArabic: Boolean): ExecutionResult {
    if (!callLogProvider.hasCallLogPermission()) {
      return ExecutionResult.RequiresPermission(
        permission = Manifest.permission.READ_CALL_LOG,
        rationale = if (isArabic) "يلزم منح صلاحية سجل المكالمات لعرض المكالمات الفائتة." else "Call log permission is required to check missed calls."
      )
    }

    val summary = callLogProvider.queryCallsSummary()
    // Mark as seen so home screen banner clears
    callLogProvider.markMissedCallsAsSeen()

    val count = summary.unreadMissedCallsCount.let { if (it > 0) it else summary.missedCallsCount }
    return if (count > 0 && summary.latestMissedCall != null) {
      val latest = summary.latestMissedCall
      val label = latest.displayLabel
      val time = latest.formattedTime
      val speech = if (isArabic) {
        "لديك $count مكالمة فائتة. آخرها من $label في تمام $time."
      } else {
        "You have $count missed call(s). Latest was from $label at $time."
      }
      ExecutionResult.Success(
        speech = speech,
        actionTaken = "Queried missed calls ($count)",
        visualState = CoreVisualState.CALL
      )
    } else {
      val speech = if (isArabic) "لا توجد مكالمات فائتة جديدة." else "You have no new missed calls."
      ExecutionResult.Success(
        speech = speech,
        actionTaken = "Queried missed calls (0)",
        visualState = CoreVisualState.RESPONDING
      )
    }
  }

  private fun handleLatestOutgoingQuery(isArabic: Boolean): ExecutionResult {
    if (!callLogProvider.hasCallLogPermission()) {
      return ExecutionResult.RequiresPermission(
        permission = Manifest.permission.READ_CALL_LOG,
        rationale = if (isArabic) "يلزم منح صلاحية سجل المكالمات لعرض المكالمات الصادرة." else "Call log permission is required to check outgoing calls."
      )
    }

    val call = callLogProvider.getLatestOutgoingCall()
    return if (call != null) {
      val speech = if (isArabic) {
        "آخر مكالمة صادرة كانت إلى ${call.displayLabel} في تمام ${call.formattedTime}."
      } else {
        "Your last outgoing call was to ${call.displayLabel} at ${call.formattedTime}."
      }
      ExecutionResult.Success(
        speech = speech,
        actionTaken = "Queried last outgoing call (${call.displayLabel})",
        visualState = CoreVisualState.CALL
      )
    } else {
      val speech = if (isArabic) "لا توجد مكالمات صادرة في سجل الهاتف." else "No outgoing calls found in your call history."
      ExecutionResult.Success(
        speech = speech,
        actionTaken = "No outgoing calls",
        visualState = CoreVisualState.RESPONDING
      )
    }
  }

  private fun handleLatestIncomingQuery(isArabic: Boolean): ExecutionResult {
    if (!callLogProvider.hasCallLogPermission()) {
      return ExecutionResult.RequiresPermission(
        permission = Manifest.permission.READ_CALL_LOG,
        rationale = if (isArabic) "يلزم منح صلاحية سجل المكالمات لعرض المكالمات الواردة." else "Call log permission is required to check incoming calls."
      )
    }

    val summary = callLogProvider.queryCallsSummary()
    val incoming = summary.latestIncomingCall
    val missed = summary.latestMissedCall

    // Compare which occurred more recently
    val latest = if (incoming != null && missed != null) {
      if (incoming.dateMillis >= missed.dateMillis) incoming else missed
    } else incoming ?: missed

    return if (latest != null) {
      val typeStr = if (latest.type == CallType.MISSED) (if (isArabic) "فائتة" else "missed") else (if (isArabic) "تم الرد عليها" else "answered")
      val speech = if (isArabic) {
        "آخر من اتصل بك هو ${latest.displayLabel} ($typeStr) في تمام ${latest.formattedTime}."
      } else {
        "Latest caller was ${latest.displayLabel} ($typeStr) at ${latest.formattedTime}."
      }
      ExecutionResult.Success(
        speech = speech,
        actionTaken = "Queried latest incoming call (${latest.displayLabel})",
        visualState = CoreVisualState.CALL
      )
    } else {
      val speech = if (isArabic) "لا توجد مكالمات واردة في سجل الهاتف." else "No incoming calls found in your call history."
      ExecutionResult.Success(
        speech = speech,
        actionTaken = "No incoming calls",
        visualState = CoreVisualState.RESPONDING
      )
    }
  }

  private fun handleAllRecentCallsQuery(isArabic: Boolean): ExecutionResult {
    if (!callLogProvider.hasCallLogPermission()) {
      return ExecutionResult.RequiresPermission(
        permission = Manifest.permission.READ_CALL_LOG,
        rationale = if (isArabic) "يلزم منح صلاحية سجل المكالمات." else "Call log permission is required."
      )
    }

    val summary = callLogProvider.queryCallsSummary()
    val recent = summary.recentCalls.take(3)
    return if (recent.isNotEmpty()) {
      val details = recent.joinToString("، ") { "${it.displayLabel} (${it.type.displayNameAr} ${it.formattedTime})" }
      val speech = if (isArabic) {
        "آخر المكالمات في السجل: $details."
      } else {
        "Recent calls: " + recent.joinToString(", ") { "${it.displayLabel} (${it.type.displayNameEn} ${it.formattedTime})" }
      }
      ExecutionResult.Success(
        speech = speech,
        actionTaken = "Queried recent calls",
        visualState = CoreVisualState.CALL
      )
    } else {
      ExecutionResult.Success(
        speech = if (isArabic) "سجل المكالمات فارغ." else "Call log is empty.",
        actionTaken = "Empty call log",
        visualState = CoreVisualState.RESPONDING
      )
    }
  }

  private fun placeOrDialCall(phoneNumber: String, contactName: String?, isArabic: Boolean): ExecutionResult {
    val hasCallPhonePermission = ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.CALL_PHONE
    ) == PackageManager.PERMISSION_GRANTED

    if (hasCallPhonePermission) {
      return try {
        val callIntent = Intent(Intent.ACTION_CALL).apply {
          data = Uri.parse("tel:$phoneNumber")
          flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(callIntent)
        val displayName = contactName ?: phoneNumber
        val responseSpeech = if (isArabic) {
          "جاري الاتصال بـ $displayName الآن..."
        } else {
          "Calling $displayName directly..."
        }
        ExecutionResult.Success(
          speech = responseSpeech,
          actionTaken = "Initiated Direct Phone Call ($phoneNumber)",
          visualState = CoreVisualState.CALL
        )
      } catch (e: Exception) {
        launchDialer(phoneNumber, contactName, isArabic)
      }
    } else {
      return ExecutionResult.RequiresPermission(
        permission = Manifest.permission.CALL_PHONE,
        rationale = if (isArabic) {
          "يلزم منح صلاحية إجراء المكالمات للاتصال بـ ${contactName ?: phoneNumber} مباشرة."
        } else {
          "Call permission is needed to directly initiate phone calls."
        }
      )
    }
  }

  private fun launchDialer(phoneNumber: String, contactName: String? = null, isArabic: Boolean = false): ExecutionResult {
    return try {
      val dialIntent = Intent(Intent.ACTION_DIAL).apply {
        data = Uri.parse("tel:$phoneNumber")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
      }
      context.startActivity(dialIntent)
      val label = if (isArabic) {
        if (contactName != null) "فتح لوحة الاتصال لـ $contactName ($phoneNumber)." else "فتح لوحة الاتصال للرقم $phoneNumber."
      } else {
        if (contactName != null) "Opening dialer for $contactName ($phoneNumber)." else "Opening dialer for $phoneNumber."
      }
      ExecutionResult.Success(
        speech = label,
        actionTaken = "Launched Phone Dialer",
        visualState = CoreVisualState.CALL
      )
    } catch (_: Exception) {
      ExecutionResult.Fallback(
        explanation = if (isArabic) "تعذر فتح تطبيق الهاتف." else "Unable to launch the phone dialer on this device."
      )
    }
  }
}
