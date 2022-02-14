package com.dragon.facedetectorflow

import android.Manifest
import android.graphics.Matrix
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dragon.facedetectorflow.camera.CameraHolder
import com.dragon.facedetectorflow.databinding.ActivityMainBinding
import com.dragon.facedetectorflow.extension.MirrorType
import com.dragon.facedetectorflow.node.Node
import com.dragon.facedetectorflow.node.NodesRender
import com.dragon.facedetectorflow.node.OesTextureNode
import com.dragon.facedetectorflow.node.PointsNode
import com.dragon.facedetectorflow.texture.CombineSurfaceTexture
import com.megvii.facepp.multi.sdk.FacePPImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var nodesRender: NodesRender
    private lateinit var gLSurfaceViewRender: GLSurfaceViewRender
    private val cameraHolder by lazy {
        CameraHolder(this)
    }

    private var faceDetector: FaceDetector? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        PermissionUtils(this, this).requestPermission(listOf(Manifest.permission.CAMERA)) { map ->
            if (map[Manifest.permission.CAMERA] == true) {
                init()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        cameraHolder.startPreview().invalidate()
        binding.glSurfaceView.onResume()
    }

    override fun onStop() {
        super.onStop()
        cameraHolder.stopPreview().invalidate()
        binding.glSurfaceView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraHolder.release().invalidate()
        faceDetector?.release()
    }

    fun init() {
        cameraHolder.cameraId = CameraHolder.CAMERA_FRONT
        nodesRender = NodesRender(720, 1280)
        gLSurfaceViewRender = GLSurfaceViewRender(binding.glSurfaceView, nodesRender)
        nodesRender.runInRender {
            //add camera preview.
            addNode(createPreviewNode())
            //add detector points.
            addNode(PointsNode(0f, 0f, 720f, 1280f))
        }
    }

    private fun createPreviewNode(): Node {
        val size = cameraHolder.previewSize(cameraHolder.cameraId, 1280, 720)
        val cameraRotation = cameraHolder.sensorOrientation()

        val matrix = Matrix()
        matrix.setRotate(90f, size.width.toFloat() / 2, size.height.toFloat() / 2)
        matrix.postTranslate((size.height - size.width) / 2.0f, (size.width - size.height) / 2.0f)
        faceDetector = FaceDetector(size.width, size.height, FacePPImage.FACE_RIGHT) { faces ->
            if (faces == null || faces.firstOrNull() == null) {
                nodesRender.haveFace = false
                return@FaceDetector
            }
            faces.firstOrNull()?.let { face ->
                face.points?.forEachIndexed { index, pointF ->
                    nodesRender.facePoints[index * 2] = pointF.x
                    nodesRender.facePoints[index * 2 + 1] = pointF.y
                }
                matrix.mapPoints(nodesRender.facePoints)
            }
            nodesRender.haveFace = true
        }

        lifecycleScope.launch(Dispatchers.IO) {
            faceDetector!!.getFaceDetectFlow(this@MainActivity).collect()
        }

        val texture = CombineSurfaceTexture(
            size.width, size.height,
            cameraRotation.toFloat(),
            if (cameraHolder.cameraId == CameraHolder.CAMERA_FRONT) MirrorType.VERTICAL_AND_HORIZONTAL else MirrorType.VERTICAL,
            { surface ->
                cameraHolder.setSurfaces(arrayOf(surface, faceDetector!!.surface)).invalidate()
            }) {
            binding.glSurfaceView.requestRender()
        }
        return OesTextureNode(
            0f,
            0f,
            nodesRender.width.toFloat(),
            nodesRender.height.toFloat(),
            texture
        )
    }

}