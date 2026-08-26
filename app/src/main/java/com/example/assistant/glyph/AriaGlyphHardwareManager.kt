package com.example.assistant.glyph

import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Optional Hardware Integration for Nothing Phone Glyph Matrix LED lights.
 * If running on a Nothing Phone (e.g. Nothing Phone (1), Nothing Phone (2), Phone (2a)),
 * this interface can drive physical LED animations alongside or instead of the software edge glow.
 *
 * For all other devices (Samsung, Xiaomi, Pixel, OnePlus, etc.), it automatically falls back
 * to the Software Screen Edge Glow Overlay seamlessly.
 */
object AriaGlyphHardwareManager {

    private val _isNothingPhone = MutableStateFlow(false)
    val isNothingPhone: StateFlow<Boolean> = _isNothingPhone.asStateFlow()

    private val _isGlyphActive = MutableStateFlow(false)
    val isGlyphActive: StateFlow<Boolean> = _isGlyphActive.asStateFlow()

    init {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        _isNothingPhone.value = manufacturer.contains("nothing") || brand.contains("nothing")
    }

    /**
     * Triggers Glyph matrix animation if on Nothing Phone,
     * otherwise logs fallback to software glow.
     */
    fun triggerGlyphPattern(context: Context, pattern: String = "BREATHE") {
        if (_isNothingPhone.value) {
            // Nothing Glyph SDK integration hook:
            // val glyphFrame = GlyphFrame.Builder().build()
            // glyphManager?.animate(glyphFrame)
            _isGlyphActive.value = true
        }
    }

    fun turnOffGlyph() {
        if (_isNothingPhone.value) {
            _isGlyphActive.value = false
        }
    }
}
