package com.example.sudokulive;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQ_CAPTURE = 1101;
    private static final int REQ_NOTIFICATIONS = 1102;

    private MediaProjectionManager projectionManager;
    private TextView overlayState;
    private WindowManager testWm;
    private View testOverlay;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        testWm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        setContentView(buildUi());
        maybeRequestNotifications();
    }

    @Override protected void onResume() {
        super.onResume();
        updateOverlayState();
    }

    @Override protected void onDestroy() {
        removeTestOverlay();
        super.onDestroy();
    }

    private View buildUi() {
        int pad = dp(20);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(28), pad, pad);
        root.setBackgroundColor(Color.rgb(247, 248, 252));

        TextView title = new TextView(this);
        title.setText("數獨即時解題");
        title.setTextSize(29);
        title.setTextColor(Color.rgb(24, 27, 36));
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        root.addView(title, matchWrap(dp(4)));

        TextView sub = new TextView(this);
        sub.setText("先測試懸浮窗 → 再啟動螢幕辨識 → 自動解數獨");
        sub.setTextSize(15);
        sub.setTextColor(Color.rgb(92, 99, 116));
        root.addView(sub, matchWrap(dp(22)));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(roundRect(Color.WHITE, dp(18), Color.rgb(229,232,240), dp(1)));
        root.addView(card, matchWrap(dp(18)));

        TextView h = new TextView(this);
        h.setText("權限 / 診斷");
        h.setTextSize(18);
        h.setTextColor(Color.rgb(35, 39, 48));
        h.setTypeface(h.getTypeface(), android.graphics.Typeface.BOLD);
        card.addView(h, matchWrap(dp(12)));

        overlayState = new TextView(this);
        overlayState.setTextSize(15);
        card.addView(overlayState, matchWrap(dp(12)));

        Button overlayBtn = makeButton("① 開啟懸浮窗權限", false);
        overlayBtn.setOnClickListener(v -> requestOverlayPermission());
        card.addView(overlayBtn, matchWrap(dp(10)));

        Button testBtn = makeButton("② 測試藍框（不錄屏）", false);
        testBtn.setOnClickListener(v -> showTestOverlay());
        card.addView(testBtn, matchWrap(dp(10)));

        if (isXiaomiFamily()) {
            Button xiaomiBtn = makeButton("POCO / Xiaomi：其他權限", false);
            xiaomiBtn.setOnClickListener(v -> openXiaomiPermissions());
            card.addView(xiaomiBtn, matchWrap(dp(10)));
        }

        Button startBtn = makeButton("③ 開始螢幕辨識", true);
        startBtn.setOnClickListener(v -> startCaptureFlow());
        card.addView(startBtn, matchWrap(dp(10)));

        Button stopBtn = makeButton("停止辨識服務", false);
        stopBtn.setOnClickListener(v -> stopCapture());
        card.addView(stopBtn, matchWrap(0));

        TextView tips = new TextView(this);
        tips.setText("先按「② 測試藍框」。\n\n如果正常：畫面中央會出現約 8 秒的半透明黑色 9×9 方框，外框是亮藍色，頂端會寫『懸浮窗測試成功』。\n\n如果測試框有出現，再按「③ 開始螢幕辨識」。如果測試框都沒有出現，就不是 OCR 問題，而是手機的懸浮窗權限被 HyperOS 擋住。");
        tips.setTextSize(15);
        tips.setTextColor(Color.rgb(77, 84, 101));
        tips.setLineSpacing(0, 1.25f);
        root.addView(tips, matchWrap(0));
        return root;
    }

    private void showTestOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            new AlertDialog.Builder(this).setTitle("懸浮窗尚未允許")
                    .setMessage("請先按①開啟『顯示在其他應用程式上層』。")
                    .setPositiveButton("去開啟", (d,w) -> requestOverlayPermission())
                    .setNegativeButton("取消", null).show();
            return;
        }
        removeTestOverlay();
        try {
            int sw = getResources().getDisplayMetrics().widthPixels;
            int sh = getResources().getDisplayMetrics().heightPixels;
            int size = Math.min((int)(sw * 0.78f), (int)(sh * 0.55f));
            size = Math.max(size, dp(260));
            testOverlay = new OverlaySmokeView(this);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    size, size,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    android.graphics.PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.CENTER;
            testWm.addView(testOverlay, lp);
            Toast.makeText(this, "如果你看到大藍框，代表懸浮窗功能正常", Toast.LENGTH_LONG).show();
            main.postDelayed(this::removeTestOverlay, 8000);
        } catch (Throwable t) {
            removeTestOverlay();
            String msg = t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage());
            new AlertDialog.Builder(this).setTitle("建立懸浮窗失敗")
                    .setMessage(msg).setPositiveButton("好", null).show();
        }
    }

    private void removeTestOverlay() {
        main.removeCallbacksAndMessages(null);
        if (testOverlay != null && testWm != null) {
            try { testWm.removeView(testOverlay); } catch (Exception ignored) {}
            testOverlay = null;
        }
    }

    private final class OverlaySmokeView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        OverlaySmokeView(Context c) { super(c); }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), cell = Math.min(w,h)/9f;
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(125, 0, 0, 0));
            c.drawRect(0,0,w,h,p);
            p.setStyle(Paint.Style.STROKE);
            p.setColor(Color.rgb(0, 145, 255));
            p.setStrokeWidth(dp(6));
            c.drawRect(dp(5),dp(5),w-dp(5),h-dp(5),p);
            for (int i=1;i<9;i++) {
                p.setStrokeWidth(i%3==0 ? dp(4) : dp(2));
                p.setColor(i%3==0 ? Color.rgb(0,175,255) : Color.argb(210,80,190,255));
                float x=cell*i;
                c.drawLine(x,0,x,h,p);
                c.drawLine(0,x,w,x,p);
            }
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.WHITE);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(dp(19));
            p.setFakeBoldText(true);
            c.drawText("懸浮窗測試成功", w/2f, dp(28), p);
        }
    }

    private Button makeButton(String text, boolean primary) {
        Button b = new Button(this);
        b.setText(text); b.setTextSize(16); b.setAllCaps(false); b.setGravity(Gravity.CENTER);
        b.setTextColor(primary ? Color.WHITE : Color.rgb(41,47,61));
        b.setBackground(roundRect(primary ? Color.rgb(46,108,246) : Color.rgb(239,242,248), dp(14), Color.TRANSPARENT, 0));
        b.setMinHeight(dp(52));
        return b;
    }

    private void updateOverlayState() {
        if (overlayState == null) return;
        boolean allowed = Settings.canDrawOverlays(this);
        overlayState.setText(allowed ? "✓ Android 回報：懸浮窗已允許" : "✗ Android 回報：懸浮窗尚未允許");
        overlayState.setTextColor(allowed ? Color.rgb(24,137,80) : Color.rgb(180,65,45));
    }

    private void requestOverlayPermission() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
        try { startActivity(intent); } catch (Exception e) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
    }

    private boolean isXiaomiFamily() {
        String m = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER;
        String b = Build.BRAND == null ? "" : Build.BRAND;
        return m.equalsIgnoreCase("Xiaomi") || b.equalsIgnoreCase("Xiaomi") || b.equalsIgnoreCase("POCO") || b.equalsIgnoreCase("Redmi");
    }

    private void openXiaomiPermissions() {
        Intent intent = new Intent("miui.intent.action.APP_PERM_EDITOR");
        intent.putExtra("extra_pkgname", getPackageName());
        try { intent.setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity"); startActivity(intent); return; } catch (Exception ignored) {}
        try { intent.setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.AppPermissionsEditorActivity"); startActivity(intent); return; } catch (Exception ignored) {}
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
    }

    private void startCaptureFlow() {
        if (!Settings.canDrawOverlays(this)) { requestOverlayPermission(); return; }
        startActivityForResult(projectionManager.createScreenCaptureIntent(), REQ_CAPTURE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_CAPTURE || resultCode != RESULT_OK || data == null) return;
        removeTestOverlay();
        Intent service = new Intent(this, ScreenCaptureService.class);
        service.setAction(ScreenCaptureService.ACTION_START);
        service.putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode);
        service.putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service); else startService(service);
        Toast.makeText(this, "正在建立數獨藍框…", Toast.LENGTH_LONG).show();
    }

    private void stopCapture() {
        Intent stop = new Intent(this, ScreenCaptureService.class);
        stop.setAction(ScreenCaptureService.ACTION_STOP);
        try { startService(stop); } catch (Exception ignored) {}
    }

    private void maybeRequestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
    }

    private LinearLayout.LayoutParams matchWrap(int bottomMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = bottomMargin; return lp;
    }

    private GradientDrawable roundRect(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(radius);
        if (strokeWidth > 0) d.setStroke(strokeWidth, strokeColor); return d;
    }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
