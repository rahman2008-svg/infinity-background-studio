package com.example.utils

import android.graphics.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object ImageProcessor {

    /**
     * Automatic color-key background removal.
     * Detects corner color or background, and sets transparent alpha for matching pixels within tolerance.
     */
    fun removeBackground(src: Bitmap, tolerance: Float, smoothness: Float, targetColor: Int? = null): Bitmap {
        val width = src.width
        val height = src.height
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        // If target color is null, sample corner pixels to detect the background
        val keyColor = targetColor ?: run {
            val corner1 = pixels[0]
            val corner2 = pixels[width - 1]
            val corner3 = pixels[(height - 1) * width]
            val corner4 = pixels[width * height - 1]
            // Average the corners
            val r = (Color.red(corner1) + Color.red(corner2) + Color.red(corner3) + Color.red(corner4)) / 4
            val g = (Color.green(corner1) + Color.green(corner2) + Color.green(corner3) + Color.green(corner4)) / 4
            val b = (Color.blue(corner1) + Color.blue(corner2) + Color.blue(corner3) + Color.blue(corner4)) / 4
            Color.rgb(r, g, b)
        }

        val kr = Color.red(keyColor)
        val kg = Color.green(keyColor)
        val kb = Color.blue(keyColor)

        // Convert tolerance to distance squared
        // Max RGB distance squared is 255^2 * 3 = 195075
        val maxDistSq = 195075f
        val thresholdSq = (tolerance / 100f) * maxDistSq
        val smoothRange = (smoothness / 100f) * maxDistSq

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            val a = Color.alpha(pixel)

            val distSq = ((r - kr) * (r - kr) + (g - kg) * (g - kg) + (b - kb) * (b - kb)).toFloat()

            if (distSq < thresholdSq) {
                // Completely transparent
                pixels[i] = Color.argb(0, r, g, b)
            } else if (distSq < thresholdSq + smoothRange && smoothRange > 0) {
                // Soft gradient transition
                val factor = (distSq - thresholdSq) / smoothRange
                val alpha = (factor * a).toInt().coerceIn(0, 255)
                pixels[i] = Color.argb(alpha, r, g, b)
            } else {
                // Keep original
                pixels[i] = pixel
            }
        }

        out.setPixels(pixels, 0, width, 0, 0, width, height)
        return out
    }

    /**
     * Applies a feather/smooth blur to the alpha channel of a bitmap.
     */
    fun featherAlpha(src: Bitmap, radius: Int): Bitmap {
        if (radius <= 0) return src
        val width = src.width
        val height = src.height
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        val tempAlpha = IntArray(width * height)
        for (i in pixels.indices) {
            tempAlpha[i] = Color.alpha(pixels[i])
        }

        // Apply a fast horizontal and vertical box blur to the alpha values
        val blurredAlpha = IntArray(width * height)
        boxBlurAlpha(tempAlpha, blurredAlpha, width, height, radius)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            pixels[i] = Color.argb(blurredAlpha[i], r, g, b)
        }

        out.setPixels(pixels, 0, width, 0, 0, width, height)
        return out
    }

    private fun boxBlurAlpha(src: IntArray, dest: IntArray, w: Int, h: Int, radius: Int) {
        val temp = IntArray(w * h)
        // Horizontal pass
        for (y in 0 until h) {
            for (x in 0 until w) {
                var sum = 0
                var count = 0
                for (kx in -radius..radius) {
                    val px = (x + kx).coerceIn(0, w - 1)
                    sum += src[y * w + px]
                    count++
                }
                temp[y * w + x] = sum / count
            }
        }
        // Vertical pass
        for (x in 0 until w) {
            for (y in 0 until h) {
                var sum = 0
                var count = 0
                for (ky in -radius..radius) {
                    val py = (y + ky).coerceIn(0, h - 1)
                    sum += temp[py * w + x]
                    count++
                }
                dest[y * w + x] = sum / count
            }
        }
    }

    /**
     * Object Eraser content-aware inpainting.
     * Replaces masked pixels (represented as colors under a path/brush) with the average surrounding neighborhood textures.
     */
    fun inpaint(src: Bitmap, mask: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        
        val srcPixels = IntArray(width * height)
        val maskPixels = IntArray(width * height)
        src.getPixels(srcPixels, 0, width, 0, 0, width, height)
        mask.getPixels(maskPixels, 0, width, 0, 0, width, height)

        // Identify masked pixels (e.g., if alpha > 10 in the mask)
        val isMasked = BooleanArray(width * height)
        for (i in maskPixels.indices) {
            isMasked[i] = Color.alpha(maskPixels[i]) > 10
        }

        val outPixels = srcPixels.clone()

        // Perform simple neighborhood-aware pixel propagation (Iterative average expansion)
        // This effectively bleeds background textures and colors into the masked areas.
        val iterations = 8
        val directions = arrayOf(
            -1 to 0, 1 to 0, 0 to -1, 0 to 1,
            -1 to -1, 1 to -1, -1 to 1, 1 to 1
        )

        for (iter in 0 until iterations) {
            val nextPixels = outPixels.clone()
            for (y in 1 until height - 1) {
                for (x in 1 until width - 1) {
                    val idx = y * width + x
                    if (isMasked[idx]) {
                        var sumR = 0
                        var sumG = 0
                        var sumB = 0
                        var count = 0
                        
                        for ((dx, dy) in directions) {
                            val nIdx = (y + dy) * width + (x + dx)
                            if (!isMasked[nIdx]) {
                                val np = outPixels[nIdx]
                                sumR += Color.red(np)
                                sumG += Color.green(np)
                                sumB += Color.blue(np)
                                count++
                            }
                        }

                        if (count > 0) {
                            nextPixels[idx] = Color.rgb(sumR / count, sumG / count, sumB / count)
                            // Remove from mask as we filled this pixel to allow propagation deeper inside
                            isMasked[idx] = false
                        }
                    }
                }
            }
            System.arraycopy(nextPixels, 0, outPixels, 0, outPixels.size)
        }

        out.setPixels(outPixels, 0, width, 0, 0, width, height)
        return out
    }

    /**
     * Portrait face-smoothing (Skin selective low-pass filter).
     * Smooths colors within skin tones, leaving other textures intact.
     */
    fun faceSmooth(src: Bitmap, amount: Float): Bitmap {
        val width = src.width
        val height = src.height
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        val smoothed = IntArray(width * height)
        // Simple fast box blur
        boxBlurRGB(pixels, smoothed, width, height, (amount / 10f).toInt().coerceIn(1, 12))

        for (i in pixels.indices) {
            val p = pixels[i]
            val r = Color.red(p)
            val g = Color.green(p)
            val b = Color.blue(p)

            // Check if color is a human skin tone
            // standard skin tone heuristics (RGB bounding box)
            val isSkin = (r > 95 && g > 40 && b > 20 &&
                    r > g && r > b && (r - g) > 15 &&
                    (max(r, max(g, b)) - min(r, min(g, b))) > 15)

            if (isSkin) {
                // Blend with blurred pixel
                val bp = smoothed[i]
                val br = Color.red(bp)
                val bg = Color.green(bp)
                val bb = Color.blue(bp)
                
                val alphaBlend = (amount / 100f).coerceIn(0f, 1f)
                val nr = (r * (1f - alphaBlend) + br * alphaBlend).toInt()
                val ng = (g * (1f - alphaBlend) + bg * alphaBlend).toInt()
                val nb = (b * (1f - alphaBlend) + bb * alphaBlend).toInt()

                pixels[i] = Color.argb(Color.alpha(p), nr, ng, nb)
            }
        }

        out.setPixels(pixels, 0, width, 0, 0, width, height)
        return out
    }

    private fun boxBlurRGB(src: IntArray, dest: IntArray, w: Int, h: Int, radius: Int) {
        val temp = IntArray(w * h)
        // Horizontal pass
        for (y in 0 until h) {
            for (x in 0 until w) {
                var sumR = 0
                var sumG = 0
                var sumB = 0
                var count = 0
                for (kx in -radius..radius) {
                    val px = (x + kx).coerceIn(0, w - 1)
                    val p = src[y * w + px]
                    sumR += Color.red(p)
                    sumG += Color.green(p)
                    sumB += Color.blue(p)
                    count++
                }
                temp[y * w + x] = Color.rgb(sumR / count, sumG / count, sumB / count)
            }
        }
        // Vertical pass
        for (x in 0 until w) {
            for (y in 0 until h) {
                var sumR = 0
                var sumG = 0
                var sumB = 0
                var count = 0
                for (ky in -radius..radius) {
                    val py = (y + ky).coerceIn(0, h - 1)
                    val p = temp[py * w + x]
                    sumR += Color.red(p)
                    sumG += Color.green(p)
                    sumB += Color.blue(p)
                    count++
                }
                dest[y * w + x] = Color.rgb(sumR / count, sumG / count, sumB / count)
            }
        }
    }

    /**
     * Teeth whitening. Whitens and brightens yellowish/cream tones.
     */
    fun teethWhitening(src: Bitmap, amount: Float): Bitmap {
        val width = src.width
        val height = src.height
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        val factor = amount / 100f

        for (i in pixels.indices) {
            val p = pixels[i]
            val r = Color.red(p)
            val g = Color.green(p)
            val b = Color.blue(p)

            // Detect yellow/off-white color range typical of teeth
            // High brightness, red/green close and higher than blue
            val isYellowish = r > 120 && g > 110 && r > b && g > b && (r - b) < 70 && (g - b) < 70

            if (isYellowish) {
                // Whitening: desaturate yellows (bring B closer to R and G) and increase brightness
                val avgRG = (r + g) / 2
                val nr = min(255, (r + (255 - r) * factor * 0.2f).toInt())
                val ng = min(255, (g + (255 - g) * factor * 0.2f).toInt())
                val nb = min(255, (b + (avgRG - b) * factor * 0.8f).toInt()) // Bring blue closer to red/green to neutralize yellow
                
                pixels[i] = Color.argb(Color.alpha(p), nr, ng, nb)
            }
        }

        out.setPixels(pixels, 0, width, 0, 0, width, height)
        return out
    }

    /**
     * Color Replace tool. Changes a selected target color to a destination replacement color.
     */
    fun replaceColor(src: Bitmap, sourceColor: Int, destColor: Int, tolerance: Float): Bitmap {
        val width = src.width
        val height = src.height
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        val sr = Color.red(sourceColor)
        val sg = Color.green(sourceColor)
        val sb = Color.blue(sourceColor)

        val dr = Color.red(destColor)
        val dg = Color.green(destColor)
        val db = Color.blue(destColor)

        val thresholdSq = (tolerance / 100f) * 195075f

        for (i in pixels.indices) {
            val p = pixels[i]
            val r = Color.red(p)
            val g = Color.green(p)
            val b = Color.blue(p)

            val distSq = ((r - sr) * (r - sr) + (g - sg) * (g - sg) + (b - sb) * (b - sb)).toFloat()

            if (distSq < thresholdSq) {
                // Smooth blend factor based on distance
                val factor = (distSq / thresholdSq)
                val nr = (dr * (1f - factor) + r * factor).toInt().coerceIn(0, 255)
                val ng = (dg * (1f - factor) + g * factor).toInt().coerceIn(0, 255)
                val nb = (db * (1f - factor) + b * factor).toInt().coerceIn(0, 255)
                pixels[i] = Color.argb(Color.alpha(p), nr, ng, nb)
            }
        }

        out.setPixels(pixels, 0, width, 0, 0, width, height)
        return out
    }

    /**
     * Photo Enhancement matrix filters.
     * Alters brightness, contrast, saturation, sharpness.
     */
    fun enhancePhoto(
        src: Bitmap,
        brightness: Float, // -100 to 100
        contrast: Float,   // -100 to 100
        saturation: Float, // -100 to 100
        sharpen: Float     // 0 to 100
    ): Bitmap {
        val width = src.width
        val height = src.height
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val canvas = Canvas(out)
        val paint = Paint()

        // Create a color matrix for Brightness, Contrast, Saturation adjustments
        val cm = ColorMatrix()

        // 1. Saturation
        val satValue = (saturation + 100f) / 100f
        cm.setSaturation(satValue)

        // 2. Brightness
        val brightValue = brightness * 2.55f // map to -255..255
        val brightMatrix = ColorMatrix(floatArrayOf(
            1f, 0f, 0f, 0f, brightValue,
            0f, 1f, 0f, 0f, brightValue,
            0f, 0f, 1f, 0f, brightValue,
            0f, 0f, 0f, 1f, 0f
        ))
        cm.postConcat(brightMatrix)

        // 3. Contrast
        val scale = (contrast + 100f) / 100f
        val translate = (-0.5f * scale + 0.5f) * 255f
        val contrastMatrix = ColorMatrix(floatArrayOf(
            scale, 0f, 0f, 0f, translate,
            0f, scale, 0f, 0f, translate,
            0f, 0f, scale, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
        cm.postConcat(contrastMatrix)

        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)

        // 4. Sharpen (Unsharp Mask filter)
        if (sharpen > 0) {
            val sharpened = applySharpenMatrix(out, sharpen)
            return sharpened
        }

        return out
    }

    private fun applySharpenMatrix(src: Bitmap, amount: Float): Bitmap {
        val width = src.width
        val height = src.height
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        val outPixels = pixels.clone()
        val factor = amount / 100f

        // Sharpen kernel:
        // [  0, -1,  0 ]
        // [ -1,  5, -1 ]
        // [  0, -1,  0 ]
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = y * width + x
                
                val center = pixels[idx]
                val top = pixels[(y - 1) * width + x]
                val bottom = pixels[(y + 1) * width + x]
                val left = pixels[y * width + (x - 1)]
                val right = pixels[y * width + (x + 1)]

                val cr = Color.red(center)
                val cg = Color.green(center)
                val cb = Color.blue(center)

                // Convolution sum
                val sumR = 5 * cr - Color.red(top) - Color.red(bottom) - Color.red(left) - Color.red(right)
                val sumG = 5 * cg - Color.green(top) - Color.green(bottom) - Color.green(left) - Color.green(right)
                val sumB = 5 * cb - Color.blue(top) - Color.blue(bottom) - Color.blue(left) - Color.blue(right)

                // Interpolate based on sharpen amount
                val nr = (cr + (sumR - cr) * factor).toInt().coerceIn(0, 255)
                val ng = (cg + (sumG - cg) * factor).toInt().coerceIn(0, 255)
                val nb = (cb + (sumB - cb) * factor).toInt().coerceIn(0, 255)

                outPixels[idx] = Color.argb(Color.alpha(center), nr, ng, nb)
            }
        }

        out.setPixels(outPixels, 0, width, 0, 0, width, height)
        return out
    }
}
