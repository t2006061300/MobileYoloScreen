package com.example.mobileyoloscreen

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.view.*
import androidx.core.app.NotificationCompat
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class ScreenDetectionService : Service() {
    companion object {
        const val EXTRA_RESULT_CODE="resultCode"; const val EXTRA_RESULT_DATA="resultData"; const val EXTRA_CONFIDENCE="confidence"
        const val CHANNEL="yolo_capture"; const val NOTIFICATION_ID=42
    }
    private var projection: MediaProjection?=null
    private var reader: ImageReader?=null
    private lateinit var overlay: OverlayView
    private lateinit var wm: WindowManager
    private val worker=Executors.newSingleThreadExecutor()
    private val busy=AtomicBoolean(false)
    private var engine:YoloEngine?=null

    override fun onCreate() {
        super.onCreate(); createChannel()
        val stop=Intent(this,ScreenDetectionService::class.java).apply { action="STOP" }
        val pi=PendingIntent.getService(this,0,stop,PendingIntent.FLAG_IMMUTABLE)
        startForeground(NOTIFICATION_ID,NotificationCompat.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("YOLO 螢幕辨識中").setContentText("點擊停止可關閉擷取").addAction(0,"停止",pi).setOngoing(true).build())
    }
    override fun onStartCommand(intent: Intent?, flags:Int, startId:Int):Int {
        if(intent?.action=="STOP"){ stopSelf(); return START_NOT_STICKY }
        if(projection!=null) return START_NOT_STICKY
        val code=intent?.getIntExtra(EXTRA_RESULT_CODE,Activity.RESULT_CANCELED)?:return START_NOT_STICKY
        val data=if(Build.VERSION.SDK_INT>=33) intent.getParcelableExtra(EXTRA_RESULT_DATA,Intent::class.java) else @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_RESULT_DATA)
        if(data==null){ stopSelf(); return START_NOT_STICKY }
        try { engine=YoloEngine(this); setupOverlay(); setupCapture(code,data,intent.getFloatExtra(EXTRA_CONFIDENCE,.45f)) }
        catch(e:Exception){ e.printStackTrace(); stopSelf() }
        return START_NOT_STICKY
    }
    private fun setupOverlay(){
        wm=getSystemService(WindowManager::class.java); overlay=OverlayView(this)
        val type=if(Build.VERSION.SDK_INT>=26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val lp=WindowManager.LayoutParams(-1,-1,type,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT)
        wm.addView(overlay,lp)
    }
    private fun setupCapture(code:Int,data:Intent,confidence:Float){
        val metrics=resources.displayMetrics; val width=metrics.widthPixels; val height=metrics.heightPixels
        reader=ImageReader.newInstance(width,height,PixelFormat.RGBA_8888,2)
        val manager=getSystemService(MediaProjectionManager::class.java)
        projection=manager.getMediaProjection(code,data).also { p ->
            p.registerCallback(object:MediaProjection.Callback(){ override fun onStop(){ stopSelf() } },Handler(Looper.getMainLooper()))
            p.createVirtualDisplay("YoloScreen",width,height,metrics.densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader!!.surface,null,null)
        }
        reader!!.setOnImageAvailableListener({ source ->
            val image=source.acquireLatestImage()?:return@setOnImageAvailableListener
            if(!busy.compareAndSet(false,true)){ image.close(); return@setOnImageAvailableListener }
            val plane=image.planes[0]; val pixelStride=plane.pixelStride; val rowStride=plane.rowStride
            val rowPadding=rowStride-pixelStride*width
            val padded=Bitmap.createBitmap(width+rowPadding/pixelStride,height,Bitmap.Config.ARGB_8888)
            padded.copyPixelsFromBuffer(plane.buffer); image.close()
            val frame=Bitmap.createBitmap(padded,0,0,width,height); padded.recycle()
            worker.execute {
                val start=System.nanoTime()
                try {
                    val detections=engine?.detect(frame,confidence).orEmpty()
                    val fps=1_000_000_000f/(System.nanoTime()-start).coerceAtLeast(1)
                    overlay.post { overlay.detections=detections; overlay.fps=fps; overlay.invalidate() }
                } catch(e:Exception){ e.printStackTrace() } finally { frame.recycle(); busy.set(false) }
            }
        },Handler(Looper.getMainLooper()))
    }
    private fun createChannel(){ if(Build.VERSION.SDK_INT>=26) getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL,"YOLO 擷取",NotificationManager.IMPORTANCE_LOW)) }
    override fun onDestroy(){ reader?.close(); projection?.stop(); engine?.close(); worker.shutdownNow(); if(::overlay.isInitialized) runCatching{wm.removeView(overlay)}; super.onDestroy() }
    override fun onBind(intent:Intent?)=null
}
