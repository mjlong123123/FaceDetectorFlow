package com.dragon.facedetectorflow.camera

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.core.app.ActivityCompat
import java.util.concurrent.CountDownLatch

class CameraHolder(private val context: Context) {
    companion object {
        const val TAG = "CameraHolder"
        const val CAMERA_REQUEST_CODE = 1

        const val CAMERA_FRONT = 1.toString()
        const val CAMERA_BACK = 0.toString()

        const val RESOLUTION_1080P_W = 1920
        const val RESOLUTION_1080P_H = 1080

        const val RESOLUTION_720P_W = 1280
        const val RESOLUTION_720P_H = 720
    }

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val handlerThread = HandlerThread("camera holder").apply { start() }
    private var handler = Handler(handlerThread.looper)

    private var requestOpen = false
    private var requestPreview = false
    private val requestPreviewSurfaces = mutableListOf<Surface>()
    private var requestRestartPreview = false
    private var requestRestartOpen = false
    private var requestRelease = false
    private var cameraPermissionInProcess = false

    var cameraId: String = CAMERA_FRONT
        set(value) {
            field = value
            runInCameraThread {
                if (cameraDevice != null) {
                    requestRestartOpen = true
                }
            }
        }
    private var cameraDevice: CameraDevice? = null
    private var cameraCaptureSession: CameraCaptureSession? = null

    init {
        cameraId = cameraManager.cameraIdList.first()
    }

    fun previewSizes(cameraId: String = this.cameraId): Array<Size> {
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val streamConfig = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        return streamConfig!!.getOutputSizes(SurfaceTexture::class.java)
    }

    fun previewSize(cameraId: String = this.cameraId, requestWidth: Int, requestHeight: Int): Size {
        return previewSizes(cameraId).sortedArrayWith(Comparator { o1, o2 ->
            var ret = o1.width - o2.width
            if (ret == 0) {
                ret = o1.height - o2.height
            }
            ret
        }).find {
            it.width >= requestWidth && it.height >= requestHeight
        } ?: previewSizes(cameraId)[0]
    }

    fun sensorOrientation(cameraId: String = this.cameraId): Int {
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        return characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
    }

    fun setSurfaces(surfaces: Array<Surface>) = runInCameraThread {
        if (cameraCaptureSession != null) {
            requestRestartPreview = true
        }
        requestPreviewSurfaces.clear()
        requestPreviewSurfaces.addAll(surfaces)
    }

    fun open() = runInCameraThread {
        requestOpen = true
    }

    fun startPreview() = runInCameraThread {
        requestOpen = true
        requestPreview = true
    }

    fun stopPreview() = runInCameraThread {
        requestPreview = false
    }

    fun close() = runInCameraThread {
        requestPreview = false
        requestOpen = false
    }

    fun release() = runInCameraThread {
        close()
        requestRelease = true
    }

    fun invalidate() = runInCameraThread {
        if (cameraPermissionInProcess) {
            Log.d(TAG, "invalidate cameraPermissionInProcess $cameraPermissionInProcess")
            return@runInCameraThread
        }
        if (cameraCaptureSession != null && (!requestPreview || requestRestartPreview || !requestOpen || requestRestartOpen)) {
            cameraCaptureSession?.close()
            cameraCaptureSession = null
            requestRestartPreview = false
            Log.d(TAG, "invalidate cameraCaptureSession?.close()")
        }

        if (cameraDevice != null && (!requestOpen || requestRestartOpen)) {
            cameraDevice?.close()
            cameraDevice = null
            requestRestartOpen = false
            Log.d(TAG, "invalidate cameraDevice?.close()")
        }

        if (cameraDevice == null && requestOpen) {
            Log.d(TAG, "invalidate openCamera()")
            openCameraInternal()
        }

        if (cameraDevice != null && cameraCaptureSession == null && requestPreview) {
            Log.d(TAG, "invalidate startPreview()")
            startPreviewInternal()
        }

        if (requestRelease) {
            Log.d(TAG, "invalidate release()")
            handlerThread.quitSafely()
        }
    }

    private fun runInCameraThread(block: CameraHolder.() -> Unit): CameraHolder {
        handler?.post { block.invoke(this) }
        return this
    }

    @SuppressLint("MissingPermission")
    private fun openCameraInternal() {
        if (!checkPermission()) {
            Log.d(TAG, "openCamera !checkPermission()")
            return
        }
        try {
            Log.d(TAG, "openCamera")
            if (cameraDevice != null) {
                Log.d(TAG, "openCamera cameraDevice != null")
                return
            }
            val countDownLatch = CountDownLatch(1)
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    Log.d(TAG, "openCamera onOpened $camera")
                    cameraDevice = camera
                    countDownLatch.countDown()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    Log.d(TAG, "openCamera onDisconnected $camera")
                    camera.close()
                    cameraDevice = null
                    countDownLatch.countDown()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.d(TAG, "openCamera onError $camera")
                    camera.close()
                    cameraDevice = null
                    countDownLatch.countDown()
                }

                override fun onClosed(camera: CameraDevice) {
                    Log.d(TAG, "openCamera onClosed $camera")
                    cameraDevice = null
                }
            }, Handler(Looper.getMainLooper()))
            Log.d(TAG, "openCamera await")
            countDownLatch.await()
            Log.d(TAG, "openCamera go on")
        } catch (ae: CameraAccessException) {
            Log.d(TAG, "openCamera CameraAccessException $ae")
            cameraDevice = null
        } catch (ie: InterruptedException) {
            Log.d(TAG, "openCamera CameraAccessException $ie")
        }
    }

    private fun startPreviewInternal() {
        Log.d(TAG, "startPreview cameraDevice $cameraDevice ")
        cameraDevice?.let { camera ->
            try {
                var availableSurfaces = requestPreviewSurfaces.filter { surface -> surface.isValid }
                if (availableSurfaces.isEmpty()) {
                    Log.d(TAG, "startPreview have not surface.")
                    return
                }
                val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                availableSurfaces.forEach { surface -> builder.addTarget(surface) }
                val countDownLatch = CountDownLatch(1)
                camera.createCaptureSession(
                    availableSurfaces,
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            Log.d(TAG, "startPreview session onConfigureFailed $session")
                            session.close()
                            cameraCaptureSession = null
                            countDownLatch.countDown()
                        }

                        override fun onConfigured(session: CameraCaptureSession) {
                            Log.d(TAG, "startPreview session onConfigured $session")
                            cameraCaptureSession = session
                            try {
                                session.setRepeatingRequest(builder.build(), null, handler)
                            } catch (e: CameraAccessException) {
                                e.printStackTrace()
                            }
                            countDownLatch.countDown()
                        }

                        override fun onClosed(session: CameraCaptureSession) {
                            Log.d(TAG, "startPreview session onClosed $session")
                            cameraCaptureSession = null
                        }
                    },
                    Handler(Looper.getMainLooper())
                )
                Log.d(TAG, "startPreview await")
                countDownLatch.await()
                Log.d(TAG, "startPreview go on")
            } catch (e: CameraAccessException) {
                e.printStackTrace()
            }
        }
    }

    private fun checkPermission(): Boolean {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionInProcess = true
            ActivityCompat.requestPermissions(context as Activity, arrayOf(Manifest.permission.CAMERA), CAMERA_REQUEST_CODE)
            return false
        }
        return true
    }

    fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == CAMERA_REQUEST_CODE) {
            cameraPermissionInProcess = false
            if (permissions.firstOrNull() == Manifest.permission.CAMERA && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                invalidate()
            }
        }
    }
}