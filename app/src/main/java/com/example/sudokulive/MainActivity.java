package com.example.sudokulive;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQ_CAPTURE = 1101;
    private static final int REQ_NOTIFICATIONS = 1102;

    private MediaProjectionManager projectionManager;
    private TextView overlayState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        setContentView(buildUi());
        maybeRequestNotifications();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateOverlayState();
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
        sub.setText("框住 9×9 棋盤 → OCR 辨識 → 自動求解 → 答案直接疊在畫面上");
        sub.setTextSize(15);
        sub.setTextColor(Color.rgb(92, 99, 116));
        sub.setLineSpacing(0, 1.25f);
        root.addView(sub, matchWrap(dp(24)));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(roundRect(Color.WHITE, dp(18), Color.rgb(229,232,240), dp(1)));
        root.addView(card, matchWrap(dp(18)));

        TextView h = new TextView(this);
        h.setText("開始前需要權限");
        h.setTextSize(18);
        h.setTextColor(Color.rgb(35, 39, 48));
        h.setTypeface(h.getTypeface(), android.graphics.Typeface.BOLD);
        card.addView(h, matchWrap(dp(12)));

        overlayState = new TextView(this);
        overlayState.setTextSize(15);
        overlayState.setTextColor(Color.rgb(75, 82, 99));
        card.addView(overlayState, matchWrap(dp(12)));

        Button overlayBtn = makeButton("① 開啟懸浮窗權限", false);
        overlayBtn.setOnClickListener(v -> requestOverlayPermission());
        card.addView(overlayBtn, matchWrap(dp(10)));

        if (isXiaomiFamily()) {
            Button xiaomiBtn = makeButton("POCO / Xiaomi：開啟其他權限", false);
            xiaomiBtn.setOnClickListener(v -> openXiaomiPermissions());
            card.addView(xiaomiBtn, matchWrap(dp(10)));

            TextView xiaomiHint = new TextView(this);
            xiaomiHint.setText("若藍色方框沒出現，請在其他權限中允許「在背景顯示彈出式視窗／背景彈出介面」之類的選項。不同 HyperOS 版本名稱可能略有差異。");
            xiaomiHint.setTextSize(13);
            xiaomiHint.setTextColor(Color.rgb(145, 82, 24));
            xiaomiHint.setLineSpacing(0, 1.2f);
            card.addView(xiaomiHint, matchWrap(dp(12)));
        }

        Button startBtn = makeButton("② 開始螢幕辨識", true);
        startBtn.setOnClickListener(v -> startCaptureFlow());
        card.addView(startBtn, matchWrap(dp(10)));

        Button stopBtn = makeButton("停止辨識服務", false);
        stopBtn.setOnClickListener(v -> stopCapture());
        card.addView(stopBtn, matchWrap(0));

        TextView tips = new TextView(this);
        tips.setText("使用：\n1. 按開始並同意螢幕錄製。\n2. 先留在本 App，等藍色 9×9 方框與控制列出現。\n3. 再切到你的數獨 App。\n4. 拖曳藍框，讓它剛好包住完整棋盤。\n5. 右下角拖曳可縮放。\n6. 點懸浮控制列「辨識」。\n7. 成功後只會在原本空格顯示答案。\n\n「鎖定」後方框會讓觸控穿透；「自動」會定期重新辨識。");
        tips.setTextSize(15);
        tips.setTextColor(Color.rgb(77, 84, 101));
        tips.setLineSpacing(0, 1.28f);
        root.addView(tips, matchWrap(0));

        return root;
    }

    private Button makeButton(String text, boolean primary) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(16);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setTextColor(primary ? Color.WHITE : Color.rgb(41, 47, 61));
        b.setBackground(roundRect(primary ? Color.rgb(46,108,246) : Color.rgb(239,242,248), dp(14), Color.TRANSPARENT, 0));
        b.setMinHeight(dp(52));
        return b;
    }

    private void updateOverlayState() {
        if (overlayState == null) return;
        boolean allowed = Settings.canDrawOverlays(this);
        overlayState.setText(allowed ? "✓ 懸浮窗：已允許" : "• 懸浮窗：尚未允許");
        overlayState.setTextColor(allowed ? Color.rgb(24, 137, 80) : Color.rgb(163, 91, 26));
    }

    private void requestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            new AlertDialog.Builder(this)
                    .setMessage("標準懸浮窗權限已開啟。若你是 POCO / Xiaomi 且仍看不到方框，請再開啟『其他權限』中的背景彈出視窗權限。")
                    .setPositiveButton("好", null)
                    .show();
            return;
        }
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    private boolean isXiaomiFamily() {
        String manufacturer = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER;
        String brand = Build.BRAND == null ? "" : Build.BRAND;
        return manufacturer.equalsIgnoreCase("Xiaomi")
                || brand.equalsIgnoreCase("Xiaomi")
                || brand.equalsIgnoreCase("POCO")
                || brand.equalsIgnoreCase("Redmi");
    }

    private void openXiaomiPermissions() {
        Intent intent = new Intent("miui.intent.action.APP_PERM_EDITOR");
        intent.putExtra("extra_pkgname", getPackageName());
        try {
            intent.setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity");
            startActivity(intent);
            return;
        } catch (Exception ignored) {}
        try {
            intent.setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.AppPermissionsEditorActivity");
            startActivity(intent);
            return;
        } catch (Exception ignored) {}
        Intent fallback = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()));
        startActivity(fallback);
    }

    private void startCaptureFlow() {
        if (!Settings.canDrawOverlays(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("需要懸浮窗權限")
                    .setMessage("先允許「顯示在其他應用程式上層」，才能把辨識框與答案疊到數獨畫面。")
                    .setPositiveButton("去開啟", (d, w) -> requestOverlayPermission())
                    .setNegativeButton("取消", null)
                    .show();
            return;
        }
        Intent captureIntent = projectionManager.createScreenCaptureIntent();
        startActivityForResult(captureIntent, REQ_CAPTURE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_CAPTURE) return;
        if (resultCode != RESULT_OK || data == null) return;

        Intent service = new Intent(this, ScreenCaptureService.class);
        service.setAction(ScreenCaptureService.ACTION_START);
        service.putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode);
        service.putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service);
        else startService(service);

        // IMPORTANT for Xiaomi/POCO/HyperOS:
        // Keep this Activity in foreground until the overlay is created. The previous
        // version immediately moved the task to background, which can make HyperOS
        // block creation of a new overlay window.
        Toast.makeText(this, "先等藍色 9×9 方框出現，再切到數獨 App", Toast.LENGTH_LONG).show();
    }

    private void stopCapture() {
        Intent stop = new Intent(this, ScreenCaptureService.class);
        stop.setAction(ScreenCaptureService.ACTION_STOP);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(stop);
        else startService(stop);
    }

    private void maybeRequestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
        }
    }

    private LinearLayout.LayoutParams matchWrap(int bottomMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = bottomMargin;
        return lp;
    }

    private GradientDrawable roundRect(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        if (strokeWidth > 0) d.setStroke(strokeWidth, strokeColor);
        return d;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
