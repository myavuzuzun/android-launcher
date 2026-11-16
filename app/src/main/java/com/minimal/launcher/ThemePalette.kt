package com.minimal.launcher

import android.view.Gravity

enum class LauncherTheme(
    val label: String,
    val isLight: Boolean,
    val bg: Int,
    val surface: Int,
    val border: Int,
    val text: Int,
    val muted: Int,
    val accent: Int,
    val pillText: Int,
    val danger: Int
) {
    AMOLED_BLACK(
        label = "amoled black",
        isLight = false,
        bg = 0xFF000000.toInt(),
        surface = 0xFF111113.toInt(),
        border = 0xFF27272A.toInt(),
        text = 0xFFF4F4F5.toInt(),
        muted = 0xFF71717A.toInt(),
        accent = 0xFFFFFFFF.toInt(),
        pillText = 0xFF09090B.toInt(),
        danger = 0xFFF87171.toInt()
    ),
    MATTE_DARK(
        label = "matte dark",
        isLight = false,
        bg = 0xFF121214.toInt(),
        surface = 0xFF1C1C20.toInt(),
        border = 0xFF2E2E34.toInt(),
        text = 0xFFE4E4E7.toInt(),
        muted = 0xFF71717A.toInt(),
        accent = 0xFFD4D4D8.toInt(),
        pillText = 0xFF121214.toInt(),
        danger = 0xFFF87171.toInt()
    ),
    NORDIC_SLATE(
        label = "nordic slate",
        isLight = false,
        bg = 0xFF18202C.toInt(),
        surface = 0xFF222D3D.toInt(),
        border = 0xFF334155.toInt(),
        text = 0xFFE2E8F0.toInt(),
        muted = 0xFF94A3B8.toInt(),
        accent = 0xFF7DD3FC.toInt(),
        pillText = 0xFF0F172A.toInt(),
        danger = 0xFFFB7185.toInt()
    ),
    WARM_PAPER(
        label = "warm paper",
        isLight = true,
        bg = 0xFFF6F4EE.toInt(),
        surface = 0xFFEAE6DC.toInt(),
        border = 0xFFD6D1C4.toInt(),
        text = 0xFF1C1917.toInt(),
        muted = 0xFF78716C.toInt(),
        accent = 0xFF292524.toInt(),
        pillText = 0xFFF6F4EE.toInt(),
        danger = 0xFFDC2626.toInt()
    ),
    PURE_LIGHT(
        label = "pure light",
        isLight = true,
        bg = 0xFFFFFFFF.toInt(),
        surface = 0xFFF3F4F6.toInt(),
        border = 0xFFE5E7EB.toInt(),
        text = 0xFF111827.toInt(),
        muted = 0xFF6B7280.toInt(),
        accent = 0xFF111827.toInt(),
        pillText = 0xFFFFFFFF.toInt(),
        danger = 0xFFDC2626.toInt()
    )
}

enum class HomeAlignment(val label: String, val gravity: Int) {
    LEFT("left", Gravity.START),
    CENTER("center", Gravity.CENTER_HORIZONTAL),
    RIGHT("right", Gravity.END)
}

enum class TextSizeOption(
    val label: String,
    val clockSp: Float,
    val homeAppSp: Float,
    val drawerAppSp: Float
) {
    SMALL("small", clockSp = 26f, homeAppSp = 16f, drawerAppSp = 14f),
    MEDIUM("medium", clockSp = 32f, homeAppSp = 19f, drawerAppSp = 16.5f),
    LARGE("large", clockSp = 38f, homeAppSp = 23f, drawerAppSp = 19.5f)
}
