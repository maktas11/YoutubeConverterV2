package com.maktas.ytconverter.data

/** A curated seed color for the Preset color-theme mode. Stored as ARGB Int, same
 *  representation as the custom-mode role overrides, so both modes share one engine. */
data class ColorPreset(val id: String, val label: String, val seed: Int)

object ColorPresets {
    val all: List<ColorPreset> = listOf(
        ColorPreset("crimson", "Crimson", 0xFFC2271B.toInt()),
        ColorPreset("ocean", "Ocean", 0xFF1B6FC2.toInt()),
        ColorPreset("emerald", "Emerald", 0xFF1E8A5F.toInt()),
        ColorPreset("violet", "Violet", 0xFF6C4FD6.toInt()),
        ColorPreset("amber", "Amber", 0xFFC98A1B.toInt()),
        ColorPreset("rose", "Rose", 0xFFD6488C.toInt()),
    )

    val default: ColorPreset get() = all.first()

    fun byId(id: String): ColorPreset = all.firstOrNull { it.id == id } ?: default
}
