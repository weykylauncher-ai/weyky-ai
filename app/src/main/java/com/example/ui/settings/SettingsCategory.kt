package com.example.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Main settings categories representing existing functional modules in WEYKY.
 * Used for the desktop-style two-pane navigation rail in landscape orientation.
 */
enum class SettingsCategory(
  val id: String,
  val title: String,
  val shortTitle: String,
  val codeTag: String,
  val icon: ImageVector,
  val description: String
) {
  APPEARANCE(
    id = "appearance",
    title = "مظهر النواة والواجهة التكتيكية (HUD)",
    shortTitle = "المظهر والواجهة",
    codeTag = "MOD.01 // APERTURE & HUD",
    icon = Icons.Default.Palette,
    description = "تخصيص أبعاد النواة، النمط الداكن والفاتح، وألوان الخلفية والإطارات"
  ),
  POWER(
    id = "power",
    title = "كفاءة الطاقة وإدارة الموارد",
    shortTitle = "كفاءة الطاقة",
    codeTag = "MOD.02 // REACTOR",
    icon = Icons.Default.BatterySaver,
    description = "ضبط معدل تموجات النواة لتوفير استهلاك المعالج الرسومي والبطارية"
  ),
  CONTEXT(
    id = "context",
    title = "محرك السياق والوعي البيئي",
    shortTitle = "محرك السياق",
    codeTag = "MOD.03 // TELEMETRY",
    icon = Icons.Default.Sensors,
    description = "قراءات حية لحساسات الحركة والميلان والتقارب والنشاط الحالي"
  ),
  LAUNCHER(
    id = "launcher",
    title = "وضع الواجهة الرئيسية (Launcher)",
    shortTitle = "الواجهة الرئيسية",
    codeTag = "MOD.04 // HOME_OS",
    icon = Icons.Default.Home,
    description = "إدارة دور WEYKY كشاشة رئيسية افتراضية للجهاز (ROLE_HOME)"
  ),
  VOICE(
    id = "voice",
    title = "منظومة الصوت والنماذج الذكية",
    shortTitle = "منظومة الصوت",
    codeTag = "MOD.05 // VOICE_CORE",
    icon = Icons.Default.GraphicEq,
    description = "المنظومة الهجينة الفصيحة، مكتبة الأصوات، والنماذج العصبية المحلية أوفلاين"
  ),
  PERMISSIONS(
    id = "permissions",
    title = "تكامل النظام والصلاحيات",
    shortTitle = "الصلاحيات والنظام",
    codeTag = "MOD.06 // SEC_CLEARANCE",
    icon = Icons.Default.Notifications,
    description = "إدارة صلاحية قراءة الإشعارات والوصول إلى خدمات الهاتف"
  ),
  ABOUT(
    id = "about",
    title = "حول WEYKY والنواة الحية",
    shortTitle = "حول WEYKY",
    codeTag = "SYS.INFO // CORE_SPEC",
    icon = Icons.Default.Info,
    description = "إصدار النظام، فلسفة النواة الحية، ومواصفات التكامل مع أندرويد"
  );

  companion object {
    fun fromId(id: String): SettingsCategory =
      entries.find { it.id == id } ?: APPEARANCE
  }
}
