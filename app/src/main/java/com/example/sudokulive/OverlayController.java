package com.example.sudokulive;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class OverlayController {
    public interface Listener {
        void onRecognize();
        void onAutoChanged(boolean enabled);
        void onStop();
    }

    private final Context context;
    private final WindowManager wm;
    private final Listener listener;
    private final int screenW;
    private final int screenH;

    private SudokuBoardView boardView;
    private WindowManager.LayoutParams boardParams;
    private LinearLayout controlView;
    private WindowManager.LayoutParams controlParams;
    private TextView statusView;
    private TextView lockButton;
    private TextView autoButton;

    private boolean locked = false;
    private boolean autoEnabled = false;
    private boolean attached = false;

    public OverlayController(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
        this.wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        screenW = dm.widthPixels;
        screenH = dm.heightPixels;
    }

    public void show() {
        if (attached) return;
        attached = true;

        int size = Math.min((int) (screenW * 0.88f), (int) (screenH * 0.58f));
        size = Math.max(size, dp(250));

        boardView = new SudokuBoardView(context);
        boardParams = new WindowManager.LayoutParams(
                size, size,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                android.graphics.PixelFormat.TRANSLUCENT);
        boardParams.gravity = Gravity.TOP | Gravity.START;
        boardParams.x = Math.max(0, (screenW - size) / 2);
        boardParams.y = Math.max(dp(120), (screenH - size) / 2);
        boardView.setLayoutParamsRef(boardParams);
        boardView.setOnFrameChanged(() -> {
            // no-op: rect is read directly from WindowManager params
        });
        wm.addView(boardView, boardParams);

        controlView = buildControls();
        controlParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT);
        controlParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        controlParams.y = dp(32);
        wm.addView(controlView, controlParams);
    }

    public void destroy() {
        if (!attached) return;
        attached = false;
        try { if (boardView != null) wm.removeView(boardView); } catch (Exception ignored) {}
        try { if (controlView != null) wm.removeView(controlView); } catch (Exception ignored) {}
        boardView = null;
        controlView = null;
    }

    public synchronized Rect getBoardRect() {
        if (boardParams == null) return new Rect();
        return new Rect(boardParams.x, boardParams.y,
                boardParams.x + boardParams.width,
                boardParams.y + boardParams.height);
    }

    public void hideBoardForCapture() {
        if (boardView != null) boardView.post(() -> boardView.setVisibility(View.INVISIBLE));
    }

    public void showBoardAfterCapture() {
        if (boardView != null) boardView.post(() -> boardView.setVisibility(View.VISIBLE));
    }

    public void setResult(int[][] puzzle, int[][] solution) {
        if (boardView != null) boardView.post(() -> boardView.setGrid(puzzle, solution));
    }

    public void clearResult() {
        if (boardView != null) boardView.post(boardView::clearGrid);
    }

    public void setStatus(String text, boolean ok) {
        if (statusView != null) {
            statusView.post(() -> {
                statusView.setText(text);
                statusView.setTextColor(ok ? Color.rgb(93, 232, 166) : Color.rgb(255, 211, 107));
            });
        }
    }

    public boolean isAutoEnabled() { return autoEnabled; }

    private LinearLayout buildControls() {
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(8), dp(6), dp(8), dp(6));
        panel.setBackground(roundRect(Color.argb(235, 24, 29, 39), dp(14), Color.argb(80,255,255,255), dp(1)));

        statusView = new TextView(context);
        statusView.setText("框住棋盤後按辨識");
        statusView.setTextColor(Color.rgb(220, 225, 235));
        statusView.setTextSize(12);
        statusView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(26));
        panel.addView(statusView, statusLp);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        panel.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView recognize = controlButton("辨識");
        recognize.setOnClickListener(v -> listener.onRecognize());
        row.addView(recognize);

        lockButton = controlButton("鎖定");
        lockButton.setOnClickListener(v -> toggleLocked());
        row.addView(lockButton);

        autoButton = controlButton("自動:關");
        autoButton.setOnClickListener(v -> {
            autoEnabled = !autoEnabled;
            autoButton.setText(autoEnabled ? "自動:開" : "自動:關");
            listener.onAutoChanged(autoEnabled);
        });
        row.addView(autoButton);

        TextView clear = controlButton("清除");
        clear.setOnClickListener(v -> clearResult());
        row.addView(clear);

        TextView stop = controlButton("停止");
        stop.setOnClickListener(v -> listener.onStop());
        row.addView(stop);

        return panel;
    }

    private TextView controlButton(String text) {
        TextView v = new TextView(context);
        v.setText(text);
        v.setTextColor(Color.WHITE);
        v.setTextSize(12);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(7), 0, dp(7), 0);
        v.setBackground(roundRect(Color.argb(28, 255,255,255), dp(9), Color.TRANSPARENT, 0));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(61), dp(38));
        lp.setMargins(dp(2), dp(2), dp(2), dp(2));
        v.setLayoutParams(lp);
        return v;
    }

    private void toggleLocked() {
        locked = !locked;
        lockButton.setText(locked ? "解鎖" : "鎖定");
        if (boardView == null || boardParams == null) return;
        if (locked) {
            boardParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            boardView.setLocked(true);
        } else {
            boardParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            boardView.setLocked(false);
        }
        try { wm.updateViewLayout(boardView, boardParams); } catch (Exception ignored) {}
    }

    private GradientDrawable roundRect(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        if (strokeWidth > 0) d.setStroke(strokeWidth, strokeColor);
        return d;
    }

    private int dp(int v) {
        return Math.round(v * context.getResources().getDisplayMetrics().density);
    }

    private final class SudokuBoardView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private WindowManager.LayoutParams params;
        private Runnable frameChanged;
        private float downRawX, downRawY;
        private int startX, startY, startSize;
        private boolean resizing;
        private boolean isLocked;
        private int[][] puzzle;
        private int[][] solution;

        SudokuBoardView(Context c) {
            super(c);
            setBackgroundColor(Color.TRANSPARENT);
            textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            setOnTouchListener(this::handleTouch);
        }

        void setLayoutParamsRef(WindowManager.LayoutParams p) { params = p; }
        void setOnFrameChanged(Runnable r) { frameChanged = r; }
        void setLocked(boolean value) { isLocked = value; invalidate(); }

        void setGrid(int[][] p, int[][] s) {
            puzzle = SudokuSolver.copy(p);
            solution = SudokuSolver.copy(s);
            invalidate();
        }

        void clearGrid() {
            puzzle = null;
            solution = null;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            float cell = Math.min(w, h) / 9f;

            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(Color.rgb(55, 151, 255));
            paint.setStrokeWidth(dp(3));
            canvas.drawRect(dp(2), dp(2), w - dp(2), h - dp(2), paint);

            for (int i = 1; i < 9; i++) {
                paint.setStrokeWidth(i % 3 == 0 ? dp(2) : Math.max(1f, dp(1)));
                paint.setColor(i % 3 == 0 ? Color.argb(215, 58, 158, 255) : Color.argb(105, 87, 180, 255));
                float p = cell * i;
                canvas.drawLine(p, 0, p, h, paint);
                canvas.drawLine(0, p, w, p, paint);
            }

            if (!isLocked) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.argb(215, 46, 108, 246));
                canvas.drawCircle(w - dp(19), h - dp(19), dp(15), paint);
                paint.setColor(Color.WHITE);
                paint.setStrokeWidth(dp(2));
                paint.setStyle(Paint.Style.STROKE);
                canvas.drawLine(w-dp(24), h-dp(14), w-dp(14), h-dp(24), paint);
                canvas.drawLine(w-dp(27), h-dp(18), w-dp(18), h-dp(27), paint);
            }

            if (puzzle != null && solution != null) {
                textPaint.setColor(Color.rgb(24, 230, 144));
                textPaint.setTextSize(cell * 0.62f);
                textPaint.setTextAlign(Paint.Align.CENTER);
                Paint.FontMetrics fm = textPaint.getFontMetrics();
                float baselineOffset = -(fm.ascent + fm.descent) / 2f;
                for (int r = 0; r < 9; r++) {
                    for (int c = 0; c < 9; c++) {
                        if (puzzle[r][c] != 0) continue;
                        String s = Integer.toString(solution[r][c]);
                        float cx = (c + 0.5f) * cell;
                        float cy = (r + 0.5f) * cell + baselineOffset;
                        canvas.drawText(s, cx, cy, textPaint);
                    }
                }
            }
        }

        private boolean handleTouch(View v, MotionEvent e) {
            if (isLocked || params == null) return false;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downRawX = e.getRawX();
                    downRawY = e.getRawY();
                    startX = params.x;
                    startY = params.y;
                    startSize = params.width;
                    resizing = e.getX() > getWidth() - dp(58) && e.getY() > getHeight() - dp(58);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int dx = Math.round(e.getRawX() - downRawX);
                    int dy = Math.round(e.getRawY() - downRawY);
                    if (resizing) {
                        int min = dp(230);
                        int max = Math.min(screenW, screenH) - dp(16);
                        int newSize = Math.max(min, Math.min(max, startSize + Math.max(dx, dy)));
                        params.width = newSize;
                        params.height = newSize;
                        params.x = Math.max(0, Math.min(screenW - newSize, params.x));
                        params.y = Math.max(0, Math.min(screenH - newSize, params.y));
                    } else {
                        params.x = Math.max(0, Math.min(screenW - params.width, startX + dx));
                        params.y = Math.max(0, Math.min(screenX - params.height, startY + dy));
                    }
                    try { wm.updateViewLayout(this, params); } catch (Exception ignored) {}
                    if (frameChanged != null) frameChanged.run();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    resizing = false;
                    return true;
                default:
                    return false;
            }
        }
    }
}
