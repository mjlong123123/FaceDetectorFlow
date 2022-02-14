package com.dragon.facedetectorflow.node

import com.dragon.facedetectorflow.extension.mesh
import com.dragon.facedetectorflow.program.FaceDistortionProgram
import com.dragon.facedetectorflow.program.PrimitiveProgram
import com.dragon.facedetectorflow.program.TextureProgram
import com.dragon.facedetectorflow.utils.OpenGlUtils

/**
 * @author dragon
 */
class ShapeNode(
    x: Float,
    y: Float,
    w: Float,
    h: Float
) : Node(x, y, w, h) {

    private val customPositionBuffer = OpenGlUtils.BufferUtils.generateFloatBuffer(3, 3)
    private val customTextureBuffer = OpenGlUtils.BufferUtils.generateFloatBuffer(3, 3)

    init {
        customTextureBuffer.clear()
        customTextureBuffer.mesh(1f, 1f, 3, 3)
        customTextureBuffer.rewind()
        customPositionBuffer.clear()
        customPositionBuffer.mesh(w, h, 3, 3)
        customPositionBuffer.rewind()
//        customPositionBuffer.indexOf(3,1,1).forEach{ index->
//            Log.d("points_index"," index $index")
//            customPositionBuffer.put(index * 2 + 1, customPositionBuffer[index * 2 + 1] + h/2/2)
//        }
//        customPositionBuffer.rewind()
    }

    override fun recreate() {
    }

    override fun render(render: NodesRender) {
        val before = render.frameBuffers?.swap()
        before ?: return

        render.program<TextureProgram>(TextureProgram::class.java).let { textureProgram ->
            textureProgram.colCount = 3
            textureProgram.rowCount = 3
            textureProgram.draw(before.textureId, customPositionBuffer, customTextureBuffer, render.openGlMatrix.mvpMatrix)
            textureProgram.colCount = 2
            textureProgram.rowCount = 2
        }

        render.program<PrimitiveProgram>(PrimitiveProgram::class.java).let { program ->
            program.draw(0, customPositionBuffer, textureCoordinateBuffer, render.openGlMatrix.mvpMatrix)
        }

        render.program<FaceDistortionProgram>(FaceDistortionProgram::class.java).let { faceDistortionProgram ->
            faceDistortionProgram.colCount = 3
            faceDistortionProgram.rowCount = 3
            faceDistortionProgram.originalPoint.clear()
            faceDistortionProgram.originalPoint.put(render.facePoints[10 * 2] / w).put(render.facePoints[10 * 2 + 1]/h)
            faceDistortionProgram.originalPoint.rewind()
            faceDistortionProgram.intensity = 0.3f
            faceDistortionProgram.faceDistortion = 0
            faceDistortionProgram.draw(before.textureId, customPositionBuffer,customTextureBuffer, render.openGlMatrix.mvpMatrix)
            faceDistortionProgram.colCount = 2
            faceDistortionProgram.rowCount = 2
        }
    }

    override fun release() {
    }
}