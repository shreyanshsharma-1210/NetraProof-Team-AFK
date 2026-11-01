package com.netraproof.app.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import kotlin.math.exp
import kotlin.math.max

/**
 * NetraProof On-Device AI Engine (100% Offline, Zero Cloud Latency).
 *
 * Runs INT8 quantized models directly on the smartphone CPU/NPU using ONNX Runtime Mobile:
 * 1. EfficientNet-B5 Ordinal Grader (27.8 MB) -> Grade 0-4 + Referral Decision (<250ms)
 * 2. U-Net SE-ResNeXt50 Lesion Segmenter (33.3 MB) -> Generates transparent lesion overlay bitmaps
 */
class NetraProofOnDevice(private val context: Context) {

    private val ortEnv = OrtEnvironment.getEnvironment()
    private var graderSession: OrtSession? = null
    private var lesionSession: OrtSession? = null

    // Ordinal thresholds for EfficientNet-B5
    private val thresholds = floatArrayOf(0.54f, 1.62f, 2.46f, 3.36f)
    private val gradeLabels = arrayOf(
        "No DR (Normal)",
        "Mild NPDR",
        "Moderate NPDR (Referable)",
        "Severe NPDR (Referable)",
        "Proliferative DR (Referable)"
    )

    init {
        // Load quantized models from Android assets folder
        val opts = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(2)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        val graderBytes = context.assets.open("netraproof_grader_b5_int8.onnx").readBytes()
        graderSession = ortEnv.createSession(graderBytes, opts)

        val lesionBytes = context.assets.open("netraproof_lesions_unet_int8.onnx").readBytes()
        lesionSession = ortEnv.createSession(lesionBytes, opts)
    }

    data class ScreeningResult(
        val grade: Int,
        val gradeLabel: String,
        val isReferable: Boolean,
        val rawRegressionScore: Float,
        val guidance: String
    )

    data class LesionOverlayResult(
        val overlayBitmap: Bitmap,
        val exudatesDetected: Boolean,
        val hemorrhagesDetected: Boolean,
        val exudatesPixelCount: Int,
        val hemorrhagesPixelCount: Int
    )

    /**
     * 1. Screen Fundus Bitmap On-Device
     */
    fun screen(fundusBitmap: Bitmap): ScreeningResult {
        val scaled = Bitmap.createScaledBitmap(fundusBitmap, 456, 456, true)
        val floatBuffer = FloatBuffer.allocate(1 * 3 * 456 * 456)

        val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
        val std = floatArrayOf(0.229f, 0.224f, 0.225f)

        val pixels = IntArray(456 * 456)
        scaled.getPixels(pixels, 0, 456, 0, 0, 456, 456)

        // CHW format normalization
        for (c in 0..2) {
            for (i in 0 until 456 * 456) {
                val p = pixels[i]
                val channelVal = when (c) {
                    0 -> Color.red(p) / 255.0f
                    1 -> Color.green(p) / 255.0f
                    else -> Color.blue(p) / 255.0f
                }
                floatBuffer.put((channelVal - mean[c]) / std[c])
            }
        }
        floatBuffer.rewind()

        val tensor = OnnxTensor.createTensor(ortEnv, floatBuffer, longArrayOf(1, 3, 456, 456))
        val output = graderSession?.run(mapOf("input" to tensor)) ?: error("Session uninitialized")
        val rawScore = ((output[0].value as Array<FloatArray>)[0][0])
        tensor.close()

        // Assign ordinal grade via calibrated cutoffs
        var grade = 0
        while (grade < 4 && rawScore >= thresholds[grade]) {
            grade++
        }

        val isReferable = grade >= 2
        val guidance = if (isReferable) {
            "Refer for comprehensive ophthalmologist examination (Grade $grade detected)."
        } else {
            "Routine annual rescreening recommended. Good microvascular preservation."
        }

        return ScreeningResult(
            grade = grade,
            gradeLabel = gradeLabels[grade],
            isReferable = isReferable,
            rawRegressionScore = rawScore,
            guidance = guidance
        )
    }

    /**
     * 2. Segment Lesions On-Device and generate transparent overlay Bitmap
     */
    fun segmentLesions(fundusBitmap: Bitmap, threshold: Float = 0.35f): LesionOverlayResult {
        val scaled = Bitmap.createScaledBitmap(fundusBitmap, 512, 512, true)
        val floatBuffer = FloatBuffer.allocate(1 * 3 * 512 * 512)

        val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
        val std = floatArrayOf(0.229f, 0.224f, 0.225f)
        val pixels = IntArray(512 * 512)
        scaled.getPixels(pixels, 0, 512, 0, 0, 512, 512)

        for (c in 0..2) {
            for (i in 0 until 512 * 512) {
                val p = pixels[i]
                val channelVal = when (c) {
                    0 -> Color.red(p) / 255.0f
                    1 -> Color.green(p) / 255.0f
                    else -> Color.blue(p) / 255.0f
                }
                floatBuffer.put((channelVal - mean[c]) / std[c])
            }
        }
        floatBuffer.rewind()

        val tensor = OnnxTensor.createTensor(ortEnv, floatBuffer, longArrayOf(1, 3, 512, 512))
        val output = lesionSession?.run(mapOf("input" to tensor)) ?: error("Session uninitialized")
        val logits = (output[0].value as Array<Array<Array<FloatArray>>>)[0] // (5, 512, 512)
        tensor.close()

        val overlayBitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        var exCount = 0
        var heCount = 0

        val exColor = Color.argb(180, 140, 241, 142) // Green for Hard Exudates
        val heColor = Color.argb(180, 68, 152, 240)  // Red/Cyan for Hemorrhages

        for (y in 0 until 512) {
            for (x in 0 until 512) {
                var maxL = logits[0][y][x]
                for (ch in 1..4) {
                    if (logits[ch][y][x] > maxL) maxL = logits[ch][y][x]
                }
                var sumExp = 0.0f
                for (ch in 0..4) {
                    sumExp += exp(logits[ch][y][x] - maxL)
                }

                val pEx = exp(logits[2][y][x] - maxL) / sumExp
                val pHe = exp(logits[3][y][x] - maxL) / sumExp

                if (pEx >= threshold) {
                    overlayBitmap.setPixel(x, y, exColor)
                    exCount++
                } else if (pHe >= threshold) {
                    overlayBitmap.setPixel(x, y, heColor)
                    heCount++
                } else {
                    overlayBitmap.setPixel(x, y, Color.TRANSPARENT)
                }
            }
        }

        return LesionOverlayResult(
            overlayBitmap = Bitmap.createScaledBitmap(overlayBitmap, fundusBitmap.width, fundusBitmap.height, true),
            exudatesDetected = exCount > 0,
            hemorrhagesDetected = heCount > 0,
            exudatesPixelCount = exCount,
            hemorrhagesPixelCount = heCount
        )
    }

    fun close() {
        graderSession?.close()
        lesionSession?.close()
        ortEnv.close()
    }
}

