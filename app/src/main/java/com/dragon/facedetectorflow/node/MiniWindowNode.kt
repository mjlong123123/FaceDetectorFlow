package com.dragon.facedetectorflow.node

import android.util.Log
import com.dragon.facedetectorflow.extension.assignPosition
import com.dragon.facedetectorflow.extension.assignTextureCoordinate
import com.dragon.facedetectorflow.extension.cropTexture
import com.dragon.facedetectorflow.program.TextureProgram

/**
 * @author dragon
 */
class MiniWindowNode(x:Float,y:Float,w:Float,h:Float): Node(x,y,w,h) {
    var textureProgram: TextureProgram?= null
    var pointX = 0f
    var pointY = 0f

    override fun recreate() {
        textureProgram = null
    }

    override fun render(render: NodesRender) {
        if(textureProgram == null) {
            textureProgram = render.program<TextureProgram>(TextureProgram::class.java)
        }
        render.frameBuffers?.let { buffers->
            val before = buffers.end()
            buffers.swap()
            //draw the bg
            positionBuffer.assignPosition(0f,0f,render.width.toFloat(),render.height.toFloat())
            textureCoordinateBuffer.assignTextureCoordinate(render.width.toFloat(),render.height.toFloat(),before.width.toFloat(),before.height.toFloat())
            textureProgram?.draw(before.textureId,positionBuffer,textureCoordinateBuffer,render.openGlMatrix.mvpMatrix)
            //draw the mini window
            positionBuffer.assignPosition(x,y,w,h)
            textureCoordinateBuffer.cropTexture(before.width.toFloat(),before.height.toFloat(),w/2,h/2,pointX,pointY)
            textureProgram?.draw(before.textureId,positionBuffer,textureCoordinateBuffer,render.openGlMatrix.mvpMatrix)
        }
    }

    var processTouch = false
    override fun onTouch(render: NodesRender, action: Int, x:Float, y:Float):Boolean{
        Log.d("dragon_touch","onTouch x $x, y $y")
        pointX = x
        pointY = y

        return true
    }

    override fun release() {

    }
}