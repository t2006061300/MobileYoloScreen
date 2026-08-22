package com.example.sudokulive;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;

import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

public class ScreenCaptureService extends Service implements OverlayController.Listener {
    public static final String ACTION_START = "com.example.sudokulive.START";
    public static final String ACTION_STOP = "com.example.sudokulive.STOP";
    public static final String EXTRA_RESULT_CODE = "resultCode";
    public static final String EXTRA_RESULT_DATA = "resultData";

    private static final String CHANNEL = "sudoku_capture";
    private static final int NOTIFICATION_ID = 2206;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean processing = new AtomicBoolean(false);
    private MediaProjection projection;
    private MediaProjection.Callback projectionCallback;
    private VirtualDisplay display;
    private ImageReader reader;
    private OverlayController overlay;
    private TextRecognizer recognizer;
    private int screenW, screenH, density;
    private volatile boolean wantFrame;
    private volatile boolean stopping;
    private long lastAuto;

    private final Runnable autoTick = new Runnable() {
        @Override public void run() {
            if (stopping) return;
            if (overlay != null && overlay.isAutoEnabled() && !processing.get() && !wantFrame) {
                long now = SystemClock.elapsedRealtime();
                if (now - lastAuto >= 1400) {
                    lastAuto = now;
                    capture("自動辨識中…");
                }
            }
            main.postDelayed(this, 300);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        if (ACTION_STOP.equals(intent.getAction())) {
            stopEverything();
            return START_NOT_STICKY;
        }
        if (!ACTION_START.equals(intent.getAction())) return START_NOT_STICKY;

        startForegroundCompat();
        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1);
        Intent resultData = Build.VERSION.SDK_INT >= 33
                ? intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent.class)
                : intent.getParcelableExtra(EXTRA_RESULT_DATA);
        if (resultCode == -1 || resultData == null) {
            stopEverything();
            return START_NOT_STICKY;
        }
        startProjection(resultCode, resultData);
        return START_NOT_STICKY;
    }

    private void startForegroundCompat() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "數獨螢幕辨識", NotificationManager.IMPORTANCE_LOW));
        }
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        Notification n = b.setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle("數獨即時解題")
                .setContentText("正在辨識螢幕中的數獨")
                .setOngoing(true).build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        else startForeground(NOTIFICATION_ID, n);
    }

    private void startProjection(int resultCode, Intent resultData) {
        if (projection != null || stopping) return;
        MediaProjectionManager mgr = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        projection = mgr.getMediaProjection(resultCode, resultData);
        if (projection == null) { stopEverything(); return; }

        projectionCallback = new MediaProjection.Callback() {
            @Override public void onStop() { main.post(() -> stopEverything()); }
        };
        projection.registerCallback(projectionCallback, main);

        DisplayMetrics dm = new DisplayMetrics();
        getSystemService(android.view.WindowManager.class).getDefaultDisplay().getRealMetrics(dm);
        screenW = dm.widthPixels;
        screenH = dm.heightPixels;
        density = dm.densityDpi;

        reader = ImageReader.newInstance(screenW, screenH, PixelFormat.RGBA_8888, 2);
        reader.setOnImageAvailableListener(this::onImage, main);
        display = projection.createVirtualDisplay("SudokuLiveCapture", screenW, screenH, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.getSurface(), null, main);

        overlay = new OverlayController(this, this);
        overlay.show();
        overlay.setStatus("框住完整 9×9 後按辨識", true);
        main.post(autoTick);
    }

    private void onImage(ImageReader imageReader) {
        Image image = null;
        try {
            image = imageReader.acquireLatestImage();
            if (image == null || !wantFrame || processing.get()) return;
            wantFrame = false;
            processing.set(true);
            Rect r = overlay == null ? new Rect() : overlay.getBoardRect();
            Bitmap full = imageToBitmap(image);
            if (full == null) { finishFailure("擷取畫面失敗"); return; }
            Rect safe = clamp(r, full.getWidth(), full.getHeight());
            Bitmap crop = Bitmap.createBitmap(full, safe.left, safe.top, safe.width(), safe.height());
            full.recycle();
            if (overlay != null) overlay.showBoardAfterCapture();
            processBoard(crop);
        } catch (Exception e) {
            finishFailure("辨識失敗，請重試");
        } finally {
            if (image != null) image.close();
        }
    }

    private Bitmap imageToBitmap(Image image) {
        Image.Plane[] planes = image.getPlanes();
        if (planes.length == 0) return null;
        Image.Plane p = planes[0];
        ByteBuffer buffer = p.getBuffer();
        int pixelStride = p.getPixelStride();
        int rowStride = p.getRowStride();
        int rowPadding = rowStride - pixelStride * screenW;
        int paddedW = screenW + rowPadding / pixelStride;
        Bitmap padded = Bitmap.createBitmap(paddedW, screenH, Bitmap.Config.ARGB_8888);
        padded.copyPixelsFromBuffer(buffer);
        Bitmap out = Bitmap.createBitmap(padded, 0, 0, screenW, screenH);
        padded.recycle();
        return out;
    }

    private void processBoard(Bitmap crop) {
        Task<Text> task = recognizer.process(InputImage.fromBitmap(crop, 0));
        task.addOnSuccessListener(result -> {
            try {
                int[][] puzzle = mapTextToGrid(result, crop.getWidth(), crop.getHeight());
                int givens = SudokuSolver.countGivens(puzzle);
                if (givens < 14) { finishFailure("只辨識到 " + givens + " 格，請對齊棋盤再試"); return; }
                if (!SudokuSolver.isValidPuzzle(puzzle)) { finishFailure("OCR 數字有衝突，請調整辨識框"); return; }
                int solutions = SudokuSolver.countSolutions(SudokuSolver.copy(puzzle), 2);
                if (solutions == 0) { finishFailure("題目無解，可能有數字辨識錯誤"); return; }
                if (solutions > 1) { finishFailure("辨識資訊不足，無法確定唯一解"); return; }
                int[][] solved = SudokuSolver.copy(puzzle);
                if (!SudokuSolver.solve(solved)) { finishFailure("求解失敗"); return; }
                if (overlay != null) {
                    overlay.setResult(puzzle, solved);
                    overlay.setStatus("完成：辨識 " + givens + " 個已知數字", true);
                }
            } finally {
                crop.recycle();
                processing.set(false);
            }
        }).addOnFailureListener(e -> {
            crop.recycle();
            finishFailure("OCR 失敗，請重試");
        });
    }

    private int[][] mapTextToGrid(Text result, int width, int height) {
        int[][] grid = new int[9][9];
        float cw = width / 9f, ch = height / 9f;
        for (Text.TextBlock block : result.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                for (Text.Element el : line.getElements()) {
                    Rect box = el.getBoundingBox();
                    if (box == null) continue;
                    String digits = normalize(el.getText());
                    if (digits.isEmpty()) continue;
                    for (int i = 0; i < digits.length(); i++) {
                        char d = digits.charAt(i);
                        if (d < '1' || d > '9') continue;
                        float x = box.left + box.width() * ((i + .5f) / digits.length());
                        float y = box.exactCenterY();
                        int col = Math.max(0, Math.min(8, (int)(x / cw)));
                        int row = Math.max(0, Math.min(8, (int)(y / ch)));
                        if (grid[row][col] == 0) grid[row][col] = d - '0';
                    }
                }
            }
        }
        return grid;
    }

    private String normalize(String raw) {
        if (raw == null) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c >= '1' && c <= '9') { out.append(c); continue; }
            switch (c) {
                case 'I': case 'l': case '|': case '!': out.append('1'); break;
                case 'Z': case 'z': out.append('2'); break;
                case 'S': case 's': out.append('5'); break;
                case 'G': out.append('6'); break;
                case 'B': out.append('8'); break;
                case 'g': case 'q': out.append('9'); break;
                default: break;
            }
        }
        return out.toString();
    }

    private Rect clamp(Rect r, int w, int h) {
        int l = Math.max(0, Math.min(w - 1, r.left));
        int t = Math.max(0, Math.min(h - 1, r.top));
        int rr = Math.max(l + 1, Math.min(w, r.right));
        int b = Math.max(t + 1, Math.min(h, r.bottom));
        return new Rect(l, t, rr, b);
    }

    private void capture(String status) {
        if (stopping || overlay == null || processing.get() || wantFrame) return;
        overlay.clearResult();
        overlay.setStatus(status, true);
        overlay.hideBoardForCapture();
        main.postDelayed(() -> wantFrame = true, 140);
    }

    private void finishFailure(String message) {
        if (overlay != null) {
            overlay.showBoardAfterCapture();
            overlay.setStatus(message, false);
        }
        wantFrame = false;
        processing.set(false);
    }

    @Override public void onRecognize() { capture("辨識中…"); }

    @Override public void onAutoChanged(boolean enabled) {
        if (overlay != null) overlay.setStatus(enabled ? "自動辨識已開啟" : "自動辨識已關閉", true);
        if (enabled) { lastAuto = 0; capture("自動辨識中…"); }
    }

    @Override public void onStop() { stopEverything(); }

    private void stopEverything() {
        if (stopping) return;
        stopping = true;
        main.removeCallbacks(autoTick);
        wantFrame = false;
        if (overlay != null) { overlay.destroy(); overlay = null; }
        if (display != null) { display.release(); display = null; }
        if (reader != null) { reader.close(); reader = null; }
        if (projection != null) {
            try { if (projectionCallback != null) projection.unregisterCallback(projectionCallback); } catch (Exception ignored) {}
            try { projection.stop(); } catch (Exception ignored) {}
            projection = null;
        }
        if (recognizer != null) { try { recognizer.close(); } catch (Exception ignored) {} recognizer = null; }
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override public void onDestroy() {
        if (!stopping) stopEverything();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
