package com.dragon.facedetectorflow.node

import android.view.MotionEvent
import com.dragon.facedetectorflow.program.PrimitiveProgram
import com.dragon.facedetectorflow.utils.OpenGlUtils

/**
 * @author dragon
 */
class PointsNode(x: Float, y: Float, w: Float, h: Float) : Node(x, y, w, h) {
    val pointPositionBuffer = OpenGlUtils.BufferUtils.generateFloatBuffer(84 * 2)
    val touchPositionBuffer = OpenGlUtils.BufferUtils.generateFloatBuffer(2)
    val facePointsOffset = FloatArray(84 * 2) { 0f }

    override fun recreate() {
    }

    override fun render(render: NodesRender) {
        render.program<PrimitiveProgram>(PrimitiveProgram::class.java).let { program ->
            if (render.haveFace) {
                pointPositionBuffer.clear()
                render.facePoints.asSequence().zip(facePointsOffset.asSequence(), { d1, d2 -> d1 + d2 }).forEach {
                    pointPositionBuffer.put(it)
                }
                pointPositionBuffer.rewind()
                program.draw(0, pointPositionBuffer, textureCoordinateBuffer, render.openGlMatrix.mvpMatrix)
            }

            if (haveDownEvent) {
                program.draw(0, touchPositionBuffer, textureCoordinateBuffer, render.openGlMatrix.mvpMatrix)
            }
        }
    }

    private var haveDownEvent = false
    private var touchPointIndex = -1
    private var downX = 0f
    private var downY = 0f
    override fun onTouch(render: NodesRender, action: Int, x: Float, y: Float): Boolean {
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                touchPositionBuffer.clear()
                touchPositionBuffer.put(x).put(y)
                touchPositionBuffer.rewind()
                if (render.haveFace) {
                    val points = render.facePoints.asSequence().zip(facePointsOffset.asSequence(), { d1, d2 -> d1 + d2 }).toList()
                    for (index in 0 until points.size step 2) {
                        if (Math.abs(x - points[index]) < 30 && Math.abs(y - points[index + 1]) < 30) {
                            haveDownEvent = true
                            touchPointIndex = index
                            downX = x - facePointsOffset[index]
                            downY = y - facePointsOffset[index + 1]
                            break
                        }
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                facePointsOffset[touchPointIndex] = x - downX
                facePointsOffset[touchPointIndex + 1] = y - downY
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                haveDownEvent = false
                return true
            }
        }
        return haveDownEvent
    }

    override fun release() {

    }
}