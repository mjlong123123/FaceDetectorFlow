package com.dragon.facedetectorflow.extension

import android.graphics.Matrix
import android.graphics.RectF
import android.util.Log
import java.nio.FloatBuffer

val textureCoordinateArray = floatArrayOf(
    0f, 0f,
    1f, 0f,
    0f, 1f,
    1f, 1f
)

val positionArray = floatArrayOf(
    -0.5f, -0.5f,
    0.5f, -0.5f,
    -0.5f, 0.5f,
    0.5f, 0.5f
)

fun FloatBuffer.assignPosition(): FloatBuffer {
    clear()
    put(positionArray)
    rewind()
    return this
}

fun FloatBuffer.mesh(w: Float, h: Float, rowCount: Int, columnCount: Int): FloatBuffer {
    val ws = w / (columnCount - 1)
    val hs = h / (rowCount - 1)
    for (row in 0 until rowCount - 1) {
        put(0f).put(row * hs)
        for (col in 0 until columnCount - 1) {
            put(col * ws).put((row + 1) * hs)
            put((col + 1) * ws).put(row * hs)
        }
        put(w).put((row + 1) * hs)
    }
    return this
}

fun FloatBuffer.indexOf(columnCount: Int, row: Int, col: Int): List<Int> {
    return intArrayOf(row * columnCount * 2 + col * 2, row * columnCount * 2 + col * 2 - (columnCount * 2 - 1)).filter { it >= 0 && it < limit()/2 }
}

/**
 * x and y is gl coordinate
 */
fun FloatBuffer.assignPosition(x: Float, y: Float, w: Float, h: Float): FloatBuffer {
    rewind()
    put(x).put(y)
    put(x + w).put(y)
    put(x).put(y + h)
    put(x + w).put(y + h)
    rewind()
    return this
}

/**
 * due to the view coordinate is different from gl environment
 *      view top(0)->bottom(viewport height)
 *      gl   top(viewport height) ->bottom(0)
 */
fun FloatBuffer.assignPosition(rectF: RectF, viewPortHeight: Float): FloatBuffer {
    rewind()
    put(rectF.left).put(viewPortHeight - rectF.bottom)
    put(rectF.right).put(viewPortHeight - rectF.bottom)
    put(rectF.left).put(viewPortHeight - rectF.top)
    put(rectF.right).put(viewPortHeight - rectF.top)
    rewind()
    return this
}

fun FloatBuffer.assignTextureCoordinate(
    viewportWidth: Float = 1f,
    viewportHeight: Float = 1f,
    textureWidth: Float = 1f,
    textureHeight: Float = 1f,
    rotate: Float = 0f,
    scaleType: ScaleType = ScaleType.CENTER_INSIDE,
    mirrorType: MirrorType = MirrorType.NONE,
    scale: Float = 1.0f
): FloatBuffer {
    val viewportRectF = RectF(0f, 0f, viewportWidth, viewportHeight)
    val textureRectF = RectF(0f, 0f, if (rotate.toInt() % 180 == 0) textureWidth else textureHeight, if (rotate.toInt() % 180 == 0) textureHeight else textureWidth)
    val scaleMatrix = Matrix()
    scaleMatrix.setRectToRect(textureRectF, viewportRectF, Matrix.ScaleToFit.CENTER)
    scaleMatrix.mapRect(textureRectF)
    var scaleX = viewportWidth / textureRectF.width()
    var scaleY = viewportHeight / textureRectF.height()

    if (scaleType == ScaleType.CROP_CENTER) {
        val scale = scaleX.coerceAtLeast(scaleY)
        scaleMatrix.reset()
        scaleMatrix.setScale(scale, scale)
        scaleMatrix.mapRect(textureRectF)
        scaleX = viewportWidth / textureRectF.width()
        scaleY = viewportHeight / textureRectF.height()
    }

    val array = FloatArray(8)
    val matrix = Matrix()
    matrix.setRotate(-rotate, 0.5f, 0.5f)
    scaleX *= scale
    scaleY *= scale
    matrix.preScale(
        if (mirrorType == MirrorType.HORIZONTAL || mirrorType == MirrorType.VERTICAL_AND_HORIZONTAL) -1f * scaleX else scaleX,
        if (mirrorType == MirrorType.VERTICAL || mirrorType == MirrorType.VERTICAL_AND_HORIZONTAL) -1f * scaleY else scaleY,
        0.5f,
        0.5f
    )
    matrix.mapPoints(array, textureCoordinateArray)
    clear()
    put(array)
    rewind()
    return this
}

fun FloatBuffer.cropTexture(
    originalTextureW: Float, originalTextureH: Float,
    croppedTextureW: Float, croppedTextureH: Float,
    pointX: Float, pointY: Float
): FloatBuffer {
    val halfW = croppedTextureW / 2
    val halfH = croppedTextureH / 2

    var left = pointX - halfW
    var right = pointX + halfW
    var top = pointY + halfH
    var bottom = pointY - halfH

    Log.d("dragon_compute", "pointX $pointX pointY $pointY")
    Log.d("dragon_compute", "left $left right $right")
    Log.d("dragon_compute", "top $top bottom $bottom")

    if (left < 0) {
        right -= left
        left = 0f
    } else if (right > originalTextureW) {
        left -= (right - originalTextureW)
        right = originalTextureW
    }

    if (bottom < 0) {
        top -= bottom
        bottom = 0f
    } else if (top > originalTextureH) {
        bottom -= (top - originalTextureH)
        top = originalTextureH
    }

    Log.d("dragon_compute", "-----originalTextureW $originalTextureW originalTextureH $originalTextureH")
    Log.d("dragon_compute", "-----pointX $pointX pointY $pointY")
    Log.d("dragon_compute", "-----left $left right $right")
    Log.d("dragon_compute", "-----top $top bottom $bottom")

    left /= originalTextureW
    right /= originalTextureW
    top /= originalTextureH
    bottom /= originalTextureH

    clear()
    put(left).put(bottom)
    put(right).put(bottom)
    put(left).put(top)
    put(right).put(top)
    rewind()
    return this
}


fun FloatBuffer.dump(tag: String, groupSize: Int = 2) {
    val sb = StringBuilder("\n")
    for (index in position() until limit() step groupSize) {
        sb.append(get(index)).append(",").append(get(index + 1)).append("\n")
    }
    Log.d("FloatBuffer", "$tag $sb")
}

enum class ScaleType {
    CROP_CENTER,
    CENTER_INSIDE
}

enum class MirrorType {
    VERTICAL,
    HORIZONTAL,
    VERTICAL_AND_HORIZONTAL,
    NONE
}
