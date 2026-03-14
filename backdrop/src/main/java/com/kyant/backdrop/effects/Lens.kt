package com.kyant.backdrop.effects

import android.graphics.RenderEffect
import android.os.Build
import androidx.annotation.FloatRange
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.fastCoerceAtLeast
import androidx.compose.ui.util.fastCoerceAtMost
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.RoundedRectRefractionShaderString
import com.kyant.backdrop.RoundedRectRefractionWithDispersionShaderString
import com.kyant.shapes.RoundedRectangularShape

fun BackdropEffectScope.lens(
    @FloatRange(from = 0.0) refractionHeight: Float,
    @FloatRange(from = 0.0) refractionAmount: Float,
    depthEffect: Boolean = false,
    chromaticAberration: Boolean = false
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    if (refractionHeight <= 0f || refractionAmount <= 0f) return

    if (padding > 0f) {
        padding = (padding - refractionHeight).fastCoerceAtLeast(0f)
    }

    val cornerRadii = fillCornerRadii(cachedLensCornerRadii)
    val effect =
        if (cornerRadii != null) {
            val shader =
                if (!chromaticAberration) {
                    obtainRuntimeShader(
                        "Refraction",
                        RoundedRectRefractionShaderString
                    )
                } else {
                    obtainRuntimeShader(
                        "RefractionWithDispersion",
                        RoundedRectRefractionWithDispersionShaderString
                    )
                }
            shader.apply {
                setFloatUniform("size", size.width, size.height)
                setFloatUniform("offset", -padding, -padding)
                setFloatUniform("cornerRadii", cornerRadii)
                setFloatUniform("refractionHeight", refractionHeight)
                setFloatUniform("refractionAmount", -refractionAmount)
                setFloatUniform("depthEffect", if (depthEffect) 1f else 0f)
                if (chromaticAberration) {
                    setFloatUniform("chromaticAberration", 1f)
                }
            }
            RenderEffect.createRuntimeShaderEffect(shader, "content")
        } else {
            throwUnsupportedSDFException()
        }
    effect(effect)
}

private val cachedLensCornerRadii = FloatArray(4)

private fun BackdropEffectScope.fillCornerRadii(out: FloatArray): FloatArray? =
    when (val shape = shape) {
        is RoundedRectangularShape -> {
            val corners = shape.corners(size, layoutDirection, this)
            out[0] = corners.topLeft
            out[1] = corners.topRight
            out[2] = corners.bottomRight
            out[3] = corners.bottomLeft
            out
        }

        is AbsoluteRoundedCornerShape -> {
            val size = size
            val maxRadius = size.minDimension / 2f
            out[0] = shape.topStart.toPx(size, this).fastCoerceAtMost(maxRadius)
            out[1] = shape.topEnd.toPx(size, this).fastCoerceAtMost(maxRadius)
            out[2] = shape.bottomEnd.toPx(size, this).fastCoerceAtMost(maxRadius)
            out[3] = shape.bottomStart.toPx(size, this).fastCoerceAtMost(maxRadius)
            out
        }

        is CornerBasedShape -> {
            val size = size
            val maxRadius = size.minDimension / 2f
            val isLtr = layoutDirection == LayoutDirection.Ltr
            out[0] = (if (isLtr) shape.topStart.toPx(size, this) else shape.topEnd.toPx(size, this)).fastCoerceAtMost(maxRadius)
            out[1] = (if (isLtr) shape.topEnd.toPx(size, this) else shape.topStart.toPx(size, this)).fastCoerceAtMost(maxRadius)
            out[2] = (if (isLtr) shape.bottomEnd.toPx(size, this) else shape.bottomStart.toPx(size, this)).fastCoerceAtMost(maxRadius)
            out[3] = (if (isLtr) shape.bottomStart.toPx(size, this) else shape.bottomEnd.toPx(size, this)).fastCoerceAtMost(maxRadius)
            out
        }

        else -> null
    }

private fun throwUnsupportedSDFException(): Nothing {
    throw UnsupportedOperationException(
        "Only RoundedRectangularShape or CornerBasedShape is supported in lens effects."
    )
}
