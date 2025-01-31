package com.Winebone.RollTheBones

import android.graphics.*
import android.graphics.drawable.Drawable
import kotlin.math.min

/**
 * Copyright (c) 2021-2025 Michael Usher
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

/**
 * Drawable implementation to show a result of a custom die, when there isn't a particular image
 * specified for that result. Result will be displayed as text over another drawable background.
 *
 * background: Drawable to use as the background of the face. Intended to be ic_generic_die_outline.
 * faceValue: String to display over the drawable; intended to be either the value of a result or
 *            a range (e.g. 2-5) for config faces of dice where the minimum isn't 1.
 */
class TextDieFace(val background: Drawable, val faceValue: String): Drawable() {
    private val textPaint = Paint()

    init {
        textPaint.color = Color.BLACK
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.style = Paint.Style.FILL
        textPaint.isAntiAlias = true
    }

    /**
     * Draw the drawable. The background will be drawn first, then the text drawn on top of that.
     * The text will be centered, and scaled so it fits in the drawable.
     * canvas: Canvas to draw on
     */
    override fun draw(canvas: Canvas) {
        background.draw(canvas)

        //Determine the size to draw the text at
        val maxTextSize = bounds.height().toFloat() / 2
        val fitHorizTextSize = bounds.width().toFloat() / faceValue.length
        textPaint.textSize = min(maxTextSize, fitHorizTextSize)

        //Determine location to draw the text at
        val textX = bounds.exactCenterX()
        val textY = bounds.exactCenterY() + (textPaint.textSize / 3)
        canvas.drawText(faceValue, textX, textY, textPaint)
    }

    /**
     * Set the alpha (transparency) of this drawable, which means the setting alpha of both the
     * background image and the text.
     * alpha: New value of the alpha
     */
    override fun setAlpha(alpha: Int) {
        background.alpha = alpha
        textPaint.alpha = alpha
    }

    /**
     * Setting a color filter on this drawable affects both the background image and the text.
     */
    override fun setColorFilter(colorFilter: ColorFilter?) {
        background.colorFilter = colorFilter
        textPaint.colorFilter = colorFilter
    }

    /**
     * Setting the tint of this drawable affects both the background image and the text.
     */
    override fun setTint(tintColor: Int) {
        super.setTint(tintColor)
        background.setTint(tintColor)
        textPaint.color = tintColor
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int {
        if(alpha == 0) return PixelFormat.TRANSPARENT
        if(background.opacity == PixelFormat.OPAQUE) return PixelFormat.OPAQUE
        return PixelFormat.TRANSLUCENT
    }

    /**
     * When the bounds change, pass that on to the background image.
     */
    override fun onBoundsChange(newBounds: Rect) {
        super.onBoundsChange(newBounds)
        background.setBounds(newBounds)
    }

    /**
     * Height is based on background image height.
     */
    override fun getIntrinsicHeight(): Int {
        return background.intrinsicHeight
    }

    /**
     * Width is based on background image width.
     */
    override fun getIntrinsicWidth(): Int {
        return background.intrinsicWidth
    }
}