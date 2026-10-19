package com.example.ui.adaptive

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveLayoutStateTest {

  @Test
  fun `compact portrait phone resolves correctly`() {
    val profile = WindowSizeResolver.resolve(
      widthDp = 360.dp,
      heightDp = 800.dp,
      isRtl = false,
      posture = DevicePosture.NORMAL
    )

    assertEquals(WindowWidthSizeClass.COMPACT, profile.widthSizeClass)
    assertEquals(WindowHeightSizeClass.MEDIUM, profile.heightSizeClass)
    assertEquals(AdaptiveOrientation.PORTRAIT, profile.orientation)
    assertEquals(AdaptiveLayoutMode.PHONE_PORTRAIT, profile.layoutMode)
    assertFalse(profile.isTablet)
    assertFalse(profile.isCompactLandscape)

    // Verify core scale calculation (default 0.44)
    val coreSize = profile.calculateAdaptiveCoreSize(0.44f)
    assertTrue("Core size should be positive and >= 88dp", coreSize >= 88.dp)
    assertTrue("Core size should not exceed available width", coreSize <= 360.dp)
  }

  @Test
  fun `compact landscape phone resolves to side-by-side layout`() {
    val profile = WindowSizeResolver.resolve(
      widthDp = 800.dp,
      heightDp = 360.dp,
      isRtl = false,
      posture = DevicePosture.NORMAL
    )

    assertEquals(WindowWidthSizeClass.MEDIUM, profile.widthSizeClass)
    assertEquals(WindowHeightSizeClass.COMPACT, profile.heightSizeClass)
    assertEquals(AdaptiveOrientation.LANDSCAPE, profile.orientation)
    assertTrue("Should be classified as compact landscape", profile.isCompactLandscape)
    assertEquals(AdaptiveLayoutMode.PHONE_LANDSCAPE, profile.layoutMode)
    assertEquals(CardDensity.COMPACT, profile.cardDensity)

    // In compact landscape, core should adapt to available height without clipping
    val coreSize = profile.calculateAdaptiveCoreSize(0.44f)
    assertTrue("Landscape core size should be at least 90dp", coreSize >= 90.dp)
    assertTrue("Landscape core size should not exceed height limit", coreSize <= 360.dp)
  }

  @Test
  fun `medium width window resolves to appropriate classes`() {
    val profile = WindowSizeResolver.resolve(
      widthDp = 640.dp,
      heightDp = 800.dp,
      isRtl = false
    )

    assertEquals(WindowWidthSizeClass.MEDIUM, profile.widthSizeClass)
    assertEquals(WindowHeightSizeClass.MEDIUM, profile.heightSizeClass)
    assertEquals(AdaptiveOrientation.PORTRAIT, profile.orientation)
  }

  @Test
  fun `expanded tablet window resolves to tablet multi-pane mode`() {
    val profile = WindowSizeResolver.resolve(
      widthDp = 1024.dp,
      heightDp = 768.dp,
      isRtl = false
    )

    assertEquals(WindowWidthSizeClass.EXPANDED, profile.widthSizeClass)
    assertEquals(WindowHeightSizeClass.MEDIUM, profile.heightSizeClass)
    assertEquals(AdaptiveOrientation.LANDSCAPE, profile.orientation)
    assertTrue(profile.isTablet)
    assertEquals(AdaptiveLayoutMode.TABLET_EXPANDED, profile.layoutMode)
    assertEquals(CardDensity.EXPANDED, profile.cardDensity)

    val coreSize = profile.calculateAdaptiveCoreSize(0.44f)
    assertTrue("Tablet core should have generous scale", coreSize >= 160.dp)
  }

  @Test
  fun `portrait tablet window resolves to tablet mode with centered core sizing`() {
    val profile = WindowSizeResolver.resolve(
      widthDp = 768.dp,
      heightDp = 1024.dp,
      isRtl = false
    )

    assertEquals(WindowWidthSizeClass.MEDIUM, profile.widthSizeClass)
    assertEquals(WindowHeightSizeClass.EXPANDED, profile.heightSizeClass)
    assertEquals(AdaptiveOrientation.PORTRAIT, profile.orientation)
    assertTrue("Should be classified as tablet", profile.isTablet)
    assertEquals(AdaptiveLayoutMode.TABLET_EXPANDED, profile.layoutMode)

    val coreSize = profile.calculateAdaptiveCoreSize(0.44f)
    assertTrue("Portrait tablet core should have generous centered scale", coreSize >= 160.dp)
    assertTrue("Portrait tablet core should not exceed width", coreSize <= 768.dp)
  }

  @Test
  fun `wide but short landscape window prevents clipping`() {
    val profile = WindowSizeResolver.resolve(
      widthDp = 960.dp,
      heightDp = 400.dp,
      isRtl = false
    )

    assertEquals(WindowWidthSizeClass.EXPANDED, profile.widthSizeClass)
    assertEquals(WindowHeightSizeClass.COMPACT, profile.heightSizeClass)
    assertTrue(profile.isCompactLandscape)
    assertEquals(AdaptiveLayoutMode.PHONE_LANDSCAPE, profile.layoutMode)
    assertEquals(CardDensity.COMPACT, profile.cardDensity)

    val coreSize = profile.calculateAdaptiveCoreSize(0.44f)
    assertTrue("Core size must be constrained by short height", coreSize <= 280.dp)
  }

  @Test
  fun `narrow split screen window resolves to compact split mode`() {
    val profile = WindowSizeResolver.resolve(
      widthDp = 340.dp,
      heightDp = 700.dp,
      isRtl = false,
      physicalScreenWidthDp = 412.dp,
      physicalScreenHeightDp = 915.dp
    )

    assertTrue("Should be detected as split screen", profile.isSplitScreen)
    assertEquals(AdaptiveLayoutMode.SPLIT_SCREEN_COMPACT, profile.layoutMode)
  }

  @Test
  fun `window resizing without orientation change updates profile dynamically`() {
    val narrowWindow = WindowSizeResolver.resolve(widthDp = 380.dp, heightDp = 800.dp)
    val wideWindow = WindowSizeResolver.resolve(widthDp = 700.dp, heightDp = 800.dp)

    assertEquals(WindowWidthSizeClass.COMPACT, narrowWindow.widthSizeClass)
    assertEquals(WindowWidthSizeClass.MEDIUM, wideWindow.widthSizeClass)
    assertEquals(AdaptiveOrientation.PORTRAIT, narrowWindow.orientation)
    assertEquals(AdaptiveOrientation.PORTRAIT, wideWindow.orientation)
  }

  @Test
  fun `arabic RTL layout flag is correctly preserved`() {
    val rtlProfile = WindowSizeResolver.resolve(
      widthDp = 400.dp,
      heightDp = 800.dp,
      isRtl = true
    )
    assertTrue("RTL flag should be true", rtlProfile.isRtl)

    val ltrProfile = WindowSizeResolver.resolve(
      widthDp = 400.dp,
      heightDp = 800.dp,
      isRtl = false
    )
    assertFalse("LTR flag should be false", ltrProfile.isRtl)
  }

  @Test
  fun `foldable tabletop posture resolves to tabletop mode`() {
    val tabletopProfile = WindowSizeResolver.resolve(
      widthDp = 680.dp,
      heightDp = 720.dp,
      isRtl = false,
      posture = DevicePosture.TABLETOP
    )

    assertEquals(DevicePosture.TABLETOP, tabletopProfile.posture)
    assertEquals(AdaptiveLayoutMode.FOLDABLE_TABLETOP, tabletopProfile.layoutMode)
  }

  @Test
  fun `foldable book posture resolves to book dual pane mode`() {
    val bookProfile = WindowSizeResolver.resolve(
      widthDp = 700.dp,
      heightDp = 750.dp,
      isRtl = false,
      posture = DevicePosture.BOOK
    )

    assertEquals(DevicePosture.BOOK, bookProfile.posture)
    assertEquals(AdaptiveLayoutMode.FOLDABLE_BOOK, bookProfile.layoutMode)
  }

  @Test
  fun `unavailable posture information defaults safely without crashes`() {
    val normalProfile = WindowSizeResolver.resolve(
      widthDp = 380.dp,
      heightDp = 780.dp,
      isRtl = false,
      posture = DevicePosture.NORMAL,
      foldFeature = null
    )

    assertEquals(DevicePosture.NORMAL, normalProfile.posture)
    assertNotNull(normalProfile.layoutMode)
    assertTrue(normalProfile.calculateAdaptiveCoreSize(0.44f) > 0.dp)
  }

  @Test
  fun `phone portrait with keyboard open maintains phone portrait layout mode and does not flip to landscape or split screen`() {
    val profile = WindowSizeResolver.resolve(
      widthDp = 412.dp,
      heightDp = 360.dp,
      isRtl = false,
      physicalScreenWidthDp = 412.dp,
      physicalScreenHeightDp = 892.dp,
      windowOrientation = AdaptiveOrientation.PORTRAIT
    )

    assertEquals(AdaptiveOrientation.PORTRAIT, profile.orientation)
    assertFalse("Keyboard opening should not be flagged as split-screen", profile.isSplitScreen)
    assertFalse("Portrait phone with keyboard open should not be classified as compact landscape", profile.isCompactLandscape)
    assertEquals("Should preserve PHONE_PORTRAIT layout when keyboard opens", AdaptiveLayoutMode.PHONE_PORTRAIT, profile.layoutMode)

    val coreSize = profile.calculateAdaptiveCoreSize(0.44f)
    assertTrue("Core size should be positive and adapt to reduced height", coreSize > 0.dp)
  }

  @Test
  fun `landscape medium window resolves to phone landscape mode and not portrait`() {
    val profile = WindowSizeResolver.resolve(
      widthDp = 750.dp,
      heightDp = 520.dp,
      isRtl = false,
      windowOrientation = AdaptiveOrientation.LANDSCAPE
    )

    assertEquals(AdaptiveOrientation.LANDSCAPE, profile.orientation)
    assertEquals(AdaptiveLayoutMode.PHONE_LANDSCAPE, profile.layoutMode)
    val coreSize = profile.calculateAdaptiveCoreSize(0.44f)
    assertTrue("Core size must adapt safely", coreSize in 90.dp..280.dp)
  }

  @Test
  fun `landscape wide window resolves to tablet expanded mode`() {
    val profile = WindowSizeResolver.resolve(
      widthDp = 1200.dp,
      heightDp = 800.dp,
      isRtl = false,
      windowOrientation = AdaptiveOrientation.LANDSCAPE
    )

    assertEquals(AdaptiveOrientation.LANDSCAPE, profile.orientation)
    assertEquals(AdaptiveLayoutMode.TABLET_EXPANDED, profile.layoutMode)
    assertEquals(CardDensity.EXPANDED, profile.cardDensity)
    assertTrue("Padding should expand appropriately", profile.contentPaddingHorizontalDp >= 16.dp)
    val coreSize = profile.calculateAdaptiveCoreSize(0.44f)
    assertTrue("Core size should be generous on wide tablet", coreSize >= 160.dp)
  }
}
