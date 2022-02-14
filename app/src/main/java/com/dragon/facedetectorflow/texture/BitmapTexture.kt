package com.dragon.facedetectorflow.texture

import android.graphics.Bitmap
import com.dragon.facedetectorflow.extension.MirrorType
import com.dragon.facedetectorflow.utils.OpenGlUtils

class BitmapTexture(
    private val bitmap: Bitmap,
    rotate: Float,
    mirrorType: MirrorType
) : BasicTexture(bitmap.width, bitmap.height, rotate, mirrorType) {
    init {
        recreate()
    }

    override fun release() {
        super.release()
        OpenGlUtils.releaseTexture(textureId)
        bitmap.recycle()
    }

    override fun recreate() {
        textureId = OpenGlUtils.createBitmapTexture(bitmap)
    }

    fun hasAlpha() = bitmap.hasAlpha()
}