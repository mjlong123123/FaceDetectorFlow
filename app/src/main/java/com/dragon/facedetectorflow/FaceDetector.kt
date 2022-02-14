package com.dragon.facedetectorflow

import android.content.Context
import android.graphics.ImageFormat
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.megvii.facepp.multi.sdk.*
import com.megvii.licensemanager.sdk.LicenseManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * @author dragon
 */
class FaceDetector(
    val width: Int,
    val height: Int,
    private val rotation: Int,
    val block: (Array<FaceDetectApi.Face>?) -> Unit
) : ImageReader.OnImageAvailableListener {
    private val tempBufferArray = ByteArray(width * height / 2)
    private var offset = 0

    private val detectThread = HandlerThread("detect thread").apply { start() }
    private val detectHandler = Handler(detectThread.looper)

    private var requestTakeLicense = false
    private val imageChannel = Channel<FacePPImage>()

    private val imageReader =
        ImageReader.newInstance(width, height, ImageFormat.YUV_420_888, 2).apply {
            setOnImageAvailableListener(this@FaceDetector, detectHandler)
        }

    private val imageBuilder =
        FacePPImage.Builder().setWidth(width).setHeight(height).setMode(FacePPImage.IMAGE_MODE_NV21)
            .setRotation(rotation)

    val surface by lazy {
        imageReader.surface
    }

    fun release() {
        detectHandler.post {
            imageReader.close()
            detectThread.quitSafely()
        }
    }

    override fun onImageAvailable(reader: ImageReader?) {
        reader ?: return
        val image = reader.acquireLatestImage() ?: return
        offset = 0
        val bufferArray = ByteArray(width * height * 3 / 2)
        image.planes[0].buffer.get(bufferArray, 0, width * height)
        offset += (width * height)
        image.planes[2].buffer.get(bufferArray, offset, image.planes[2].buffer.remaining())
        image.planes[1].buffer.get(tempBufferArray, 0, image.planes[1].buffer.remaining())
        for (index in tempBufferArray.indices step 2) {
            bufferArray[offset + 1 + index] = tempBufferArray[index]
        }
        image.close()
        try {
            imageChannel.trySend(imageBuilder.setData(bufferArray).build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    private suspend fun takeLicense(context: Context, modelBuffer: ByteArray) =
        suspendCancellableCoroutine<Int> { continuation ->
            var ability = FaceppApi.getInstance().getModelAbility(modelBuffer)
            val authManager = FacePPMultiAuthManager(ability)
            val licenseManager = LicenseManager(context)
            licenseManager.registerLicenseManager(authManager)
            val uuid: String = Util.getUUIDString(context)
            licenseManager.takeLicenseFromNetwork(
                Util.CN_LICENSE_URL, uuid, Util.API_KEY, Util.API_SECRET,
                "1", object : LicenseManager.TakeLicenseCallback {
                    override fun onSuccess() {
                        continuation.resumeWith(Result.success(0))
                    }

                    override fun onFailed(i: Int, bytes: ByteArray) {
                        continuation.resumeWith(Result.success(1))
                    }
                })
        }

    fun getFaceDetectFlow(context: Context): Flow<Array<FaceDetectApi.Face>> {
        var modelBuffer: ByteArray? = null

        return flow {
            while (currentCoroutineContext().isActive) {
                emit(imageChannel.receive())
            }
        }.onStart {
            var ret = -1
            context.assets.open("megviifacepp_model").use { ios ->
                modelBuffer = ByteArray(ios.available())
                ios.read(modelBuffer)
                FaceppApi.getInstance().setLogLevel(4)
                ret = FaceppApi.getInstance().initHandle(modelBuffer)
            }
            if (ret != 0) {
                Log.d("dragon_debug", " onStart open failed!")
                throw RuntimeException("init")
            }
            if (requestTakeLicense && modelBuffer != null) {
                Log.d("dragon_debug", " onStart takeLicense")
                ret = takeLicense(context, modelBuffer!!)
            }
            if (ret != 0) {
                Log.d("dragon_debug", " onStart takeLicense failed!")
                throw RuntimeException("takeLicense")
            }
            ret = FaceDetectApi.getInstance().initFaceDetect()
            DLmkDetectApi.getInstance().initDLmkDetect()
            if (ret != 0) {
                if (requestTakeLicense) {
                    Log.d("dragon_debug", " onStart initFaceDetect error")
                    throw RuntimeException("error")
                }
                requestTakeLicense = true
                Log.d("dragon_debug", " onStart initFaceDetect retry exception")
                throw RuntimeException("initFace")
            }
            val config = FaceDetectApi.getInstance().faceppConfig
            config.face_confidence_filter = 0.6f
            config.detectionMode = FaceDetectApi.FaceppConfig.DETECTION_MODE_TRACKING
            FaceDetectApi.getInstance().faceppConfig = config
        }.map { image ->
            val faces = FaceDetectApi.getInstance().detectFace(image)
            faces.forEach { face ->
                FaceDetectApi.getInstance().getLandmark(face, FaceDetectApi.LMK_84, true)
            }
            block.invoke(faces)
            faces
        }.onCompletion {
            Log.d("dragon_debug", " onCompletion ")
            FaceppApi.getInstance().ReleaseHandle()
            DLmkDetectApi.getInstance().releaseDlmDetect()
        }.retryWhen { cause, attempt ->
            Log.d("dragon_debug", " retryWhen $cause attempt $attempt")
            if (attempt > 1) {
                false
            } else {
                (cause as? RuntimeException)?.message?.equals("initFace") ?: false
            }
        }.catch {
            Log.d("dragon_debug", " catch e $it")
        }
    }
}