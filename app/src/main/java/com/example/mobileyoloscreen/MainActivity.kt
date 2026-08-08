package com.example.mobileyoloscreen

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var threshold: SeekBar

    private val modelPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            ModelStore.modelUri = it
            status.text = "已選擇模型：${it.lastPathSegment}"
        }
    }
    private val labelsPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            ModelStore.labels = contentResolver.openInputStream(it)?.bufferedReader()?.readLines()
                ?.map(String::trim)?.filter(String::isNotEmpty).orEmpty()
            status.text = "已載入 ${ModelStore.labels.size} 個標籤"
        }
    }
    private val captureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val intent = Intent(this, ScreenDetectionService::class.java).apply {
                putExtra(ScreenDetectionService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenDetectionService.EXTRA_RESULT_DATA, result.data)
                putExtra(ScreenDetectionService.EXTRA_CONFIDENCE, threshold.progress / 100f)
            }
            ContextCompat.startForegroundService(this, intent)
            status.text = "辨識服務執行中"
        } else status.text = "未授予螢幕擷取權限"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (20 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad)
            gravity = Gravity.CENTER_HORIZONTAL; setBackgroundColor(Color.rgb(16, 20, 24))
        }
        fun title(text: String, size: Float) = TextView(this).apply {
            this.text = text; textSize = size; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setPadding(0, pad / 2, 0, pad / 2)
        }
        root.addView(title("Mobile YOLO Screen", 27f))
        root.addView(title("ONNX 螢幕即時辨識與浮動框", 15f))
        status = title("請先選擇 ONNX 模型", 14f); root.addView(status)
        fun button(text: String, click: () -> Unit) = Button(this).apply {
            this.text = text; setOnClickListener { click() }
            root.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = pad / 2 })
        }
        button("1. 選擇 ONNX 模型") { modelPicker.launch(arrayOf("application/octet-stream", "*/*")) }
        button("2. 選擇 labels.txt（選用）") { labelsPicker.launch(arrayOf("text/plain")) }
        root.addView(title("信心門檻：預設 45%", 14f))
        threshold = SeekBar(this).apply { max = 95; min = 10; progress = 45 }
        root.addView(threshold, LinearLayout.LayoutParams(-1, -2))
        button("3. 開始螢幕辨識") { startDetection() }
        button("停止辨識") { stopService(Intent(this, ScreenDetectionService::class.java)); status.text = "已停止" }
        root.addView(title("提示：受保護的 DRM／安全畫面無法被擷取。", 12f))
        setContentView(ScrollView(this).apply { addView(root) })
        requestNotificationPermission()
    }

    private fun startDetection() {
        if (ModelStore.modelUri == null) { status.text = "請先選擇 ONNX 模型"; return }
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            status.text = "請允許顯示在其他應用程式上層，再按一次開始"
            return
        }
        val manager = getSystemService(MediaProjectionManager::class.java)
        captureLauncher.launch(manager.createScreenCaptureIntent())
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
    }
}
