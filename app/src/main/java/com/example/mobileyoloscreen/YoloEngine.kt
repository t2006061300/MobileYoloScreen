package com.example.mobileyoloscreen

import ai.onnxruntime.*
import android.content.Context
import android.graphics.Bitmap
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min

class YoloEngine(context: Context) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val inputName: String
    private val inputW: Int
    private val inputH: Int

    init {
        val uri = requireNotNull(ModelStore.modelUri) { "尚未選擇模型" }
        val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        val options = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            try { addNnapi() } catch (_: Exception) { }
        }
        session = env.createSession(bytes, options)
        inputName = session.inputNames.first()
        val shape = (session.inputInfo[inputName]!!.info as TensorInfo).shape
        inputH = shape.getOrNull(2)?.takeIf { it > 0 }?.toInt() ?: 640
        inputW = shape.getOrNull(3)?.takeIf { it > 0 }?.toInt() ?: 640
    }

    fun detect(bitmap: Bitmap, confidence: Float): List<Detection> {
        val resized = Bitmap.createScaledBitmap(bitmap, inputW, inputH, true)
        val pixels = IntArray(inputW * inputH)
        resized.getPixels(pixels, 0, inputW, 0, 0, inputW, inputH)
        val data = FloatArray(3 * inputW * inputH)
        val plane = inputW * inputH
        pixels.forEachIndexed { i, p ->
            data[i] = ((p shr 16) and 255) / 255f
            data[plane + i] = ((p shr 8) and 255) / 255f
            data[2 * plane + i] = (p and 255) / 255f
        }
        OnnxTensor.createTensor(env, FloatBuffer.wrap(data), longArrayOf(1, 3, inputH.toLong(), inputW.toLong())).use { tensor ->
            session.run(mapOf(inputName to tensor)).use { result ->
                val value = result[0].value
                val rows = normalizeOutput(value)
                return nms(decode(rows, bitmap.width, bitmap.height, confidence), 0.45f)
            }
        }
    }

    private fun normalizeOutput(value: Any): Array<FloatArray> {
        val batch = value as Array<*>
        val matrix = batch[0] as Array<*>
        val rows = matrix.map { it as FloatArray }.toTypedArray()
        if (rows.isEmpty()) return emptyArray()
        // Ultralytics 通常輸出 [4+C, N]；N 遠大於欄位數時轉置。
        return if (rows.size < rows[0].size) Array(rows[0].size) { n -> FloatArray(rows.size) { c -> rows[c][n] } } else rows
    }

    private fun decode(rows: Array<FloatArray>, screenW: Int, screenH: Int, conf: Float): List<Detection> {
        val out = mutableListOf<Detection>()
        for (r in rows) {
            if (r.size < 6) continue
            if (r.size == 6) {
                val score = r[4]; if (score < conf) continue
                val normalized = max(max(r[0], r[1]), max(r[2], r[3])) <= 2f
                val sx = if (normalized) screenW.toFloat() else screenW.toFloat() / inputW
                val sy = if (normalized) screenH.toFloat() else screenH.toFloat() / inputH
                out += make(r[0] * sx, r[1] * sy, r[2] * sx, r[3] * sy, score, r[5].toInt(), screenW, screenH)
                continue
            }
            var cls = 0; var score = -1f
            for (i in 4 until r.size) if (r[i] > score) { score = r[i]; cls = i - 4 }
            if (score < conf) continue
            val normalized = max(max(r[0], r[1]), max(r[2], r[3])) <= 2f
            val sx = if (normalized) screenW.toFloat() else screenW.toFloat() / inputW
            val sy = if (normalized) screenH.toFloat() else screenH.toFloat() / inputH
            val cx = r[0] * sx; val cy = r[1] * sy; val w = r[2] * sx; val h = r[3] * sy
            out += make(cx-w/2, cy-h/2, cx+w/2, cy+h/2, score, cls, screenW, screenH)
        }
        return out
    }

    private fun make(l: Float,t: Float,r: Float,b: Float,s: Float,c: Int,w: Int,h: Int): Detection {
        val label = ModelStore.labels.getOrNull(c) ?: "class$c"
        return Detection(l.coerceIn(0f,w.toFloat()), t.coerceIn(0f,h.toFloat()), r.coerceIn(0f,w.toFloat()), b.coerceIn(0f,h.toFloat()), s,c,label)
    }
    private fun iou(a: Detection,b: Detection): Float {
        val inter = max(0f,min(a.right,b.right)-max(a.left,b.left))*max(0f,min(a.bottom,b.bottom)-max(a.top,b.top))
        val aa=(a.right-a.left)*(a.bottom-a.top); val ba=(b.right-b.left)*(b.bottom-b.top)
        return inter/(aa+ba-inter+1e-6f)
    }
    private fun nms(items: List<Detection>, threshold: Float): List<Detection> {
        val sorted=items.sortedByDescending { it.score }; val keep=mutableListOf<Detection>()
        for (d in sorted) if (keep.none { it.classId==d.classId && iou(it,d)>threshold }) keep+=d
        return keep.take(100)
    }
    override fun close() { session.close() }
}
