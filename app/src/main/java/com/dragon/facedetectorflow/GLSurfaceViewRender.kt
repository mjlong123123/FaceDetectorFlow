package com.dragon.facedetectorflow

import android.graphics.Matrix
import android.graphics.RectF
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import android.view.*
import androidx.core.view.GestureDetectorCompat
import com.dragon.facedetectorflow.extension.assignPosition
import com.dragon.facedetectorflow.extension.assignTextureCoordinate
import com.dragon.facedetectorflow.node.NodesRender
import com.dragon.facedetectorflow.program.TextureProgram
import com.dragon.facedetectorflow.utils.MVPMatrix
import com.dragon.facedetectorflow.utils.OpenGlUtils
import javax.microedition.khronos.egl.*
import javax.microedition.khronos.opengles.GL10

class GLSurfaceViewRender(
    private val glSurfaceView: GLSurfaceView,
    private val nodesRender: NodesRender
) : GLSurfaceView.Renderer, View.OnTouchListener {
    companion object {
        const val TAG = "CustomRender"
    }

    private var scaleGestureDetector: ScaleGestureDetector
    private var gestureDetectorCompat: GestureDetectorCompat

    private val displayPosition = OpenGlUtils.BufferUtils.generateFloatBuffer(8)
    private val displayTextureCoordinate = OpenGlUtils.BufferUtils.generateFloatBuffer(8)

    private var displayProgram: TextureProgram? = null
    private var mvpMatrix = MVPMatrix()

    private val viewPortRectF = RectF()
    private val displayRectF = RectF()

    init {
        glSurfaceView.apply {
            setEGLContextClientVersion(3)
            setRenderer(this@GLSurfaceViewRender)
            renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
            preserveEGLContextOnPause = false
            scaleGestureDetector = ScaleGestureDetector(glSurfaceView.context, object : ScaleGestureDetector.OnScaleGestureListener {
                private val scaleMatrix = Matrix()
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    scaleMatrix.reset()
                    scaleMatrix.setScale(detector.scaleFactor, detector.scaleFactor, displayRectF.centerX(), displayRectF.centerY())
                    scaleMatrix.mapRect(displayRectF)
                    return true
                }

                override fun onScaleBegin(detector: ScaleGestureDetector?): Boolean {
                    return true
                }

                override fun onScaleEnd(detector: ScaleGestureDetector?) {
                }
            })
            gestureDetectorCompat = GestureDetectorCompat(glSurfaceView.context, object : GestureDetector.OnGestureListener {
                override fun onDown(e: MotionEvent?): Boolean {
                    return true
                }

                override fun onShowPress(e: MotionEvent?) {
                }

                override fun onSingleTapUp(e: MotionEvent?): Boolean {
                    return true
                }

                override fun onScroll(e1: MotionEvent?, e2: MotionEvent?, distanceX: Float, distanceY: Float): Boolean {
                    displayRectF.offset(-distanceX, -distanceY)
                    return true
                }

                override fun onLongPress(e: MotionEvent?) {
                }

                override fun onFling(e1: MotionEvent?, e2: MotionEvent?, velocityX: Float, velocityY: Float): Boolean {
                    return true
                }
            })
            glSurfaceView.setOnTouchListener(this@GLSurfaceViewRender)
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.d(TAG, "onSurfaceCreated gl $gl")
        gl?.let {
            if (nodesRender.updateGL()) {
                displayProgram = null
            }
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        Log.d(TAG, "onSurfaceChanged gl $gl width $width height $height ")
        viewPortRectF.set(0f, 0f, width.toFloat(), height.toFloat())
        displayRectF.set(0f, 0f, nodesRender.width.toFloat(), nodesRender.height.toFloat())
        val tempMatrix = Matrix()
        tempMatrix.setRectToRect(displayRectF, viewPortRectF, Matrix.ScaleToFit.CENTER)
        tempMatrix.mapRect(displayRectF)
        mvpMatrix = MVPMatrix().updateViewport(width, height)
        displayTextureCoordinate.assignTextureCoordinate()
    }

    override fun onDrawFrame(gl: GL10?) {
        val frontBuffer = nodesRender.render()
        if (displayProgram == null) {
            displayProgram = TextureProgram()
        }
        frontBuffer ?: return
        GLES20.glViewport(0, 0, viewPortRectF.width().toInt(), viewPortRectF.height().toInt())
        GLES20.glClearColor(1.0f, 1.0f, 1.0f, 1.0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        displayPosition.assignPosition(displayRectF, viewPortRectF.height())
        displayProgram!!.draw(
            frontBuffer.textureId,
            displayPosition,
            displayTextureCoordinate,
            mvpMatrix.mvpMatrix
        )
    }

    override fun onTouch(v: View?, event: MotionEvent): Boolean{
        if (nodesRender.onTouch(event.actionMasked, (event.x - displayRectF.left) / displayRectF.width() *nodesRender.width, (displayRectF.height() - (event.y - displayRectF.top)) / displayRectF.height() * nodesRender.height)) {
            return true
        }
        scaleGestureDetector.onTouchEvent(event)
        gestureDetectorCompat.onTouchEvent(event)
        glSurfaceView.requestRender()
        return true
    }
}