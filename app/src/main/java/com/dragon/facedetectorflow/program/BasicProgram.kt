package com.dragon.facedetectorflow.program

import com.dragon.facedetectorflow.utils.OpenGlUtils
import java.nio.FloatBuffer

abstract class BasicProgram(
    vertexShader: String,
    fragmentShader: String
) {
    val programHandle = OpenGlUtils.createProgram(
        vertexShader,
        fragmentShader
    )
    private var released: Boolean = false
    abstract fun draw(
        textureId: Int,
        position: FloatBuffer,
        textureCoordinate: FloatBuffer,
        mvp: FloatArray
    )

    fun release() {
        OpenGlUtils.destroyProgram(programHandle)
        released = true
    }

    fun isReleased() = released
}