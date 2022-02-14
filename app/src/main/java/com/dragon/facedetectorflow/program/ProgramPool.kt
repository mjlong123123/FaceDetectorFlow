package com.dragon.facedetectorflow.program

/**
 * @author dragon
 */
class ProgramPool {
    private val programMap = mutableMapOf<String, BasicProgram>()
    private val programCreators = mutableMapOf(
        Pair(OesTextureProgram::class.java.name, {
            OesTextureProgram()
        }),
        Pair(PrimitiveProgram::class.java.name, {
            PrimitiveProgram()
        }),
        Pair(TextureProgram::class.java.name, {
            TextureProgram()
        }),
        Pair(FaceDistortionProgram::class.java.name,{
            FaceDistortionProgram()
        })
    )

    fun clear() = programMap.clear()

    operator fun get(key: String) = programMap[key] ?: programCreators[key]?.invoke()?.apply { programMap[key] = this }
}