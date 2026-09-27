package com.example.floatingclock;

import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

public class DesktopClockActivity extends AppCompatActivity {
    private static final long BURN_IN_MOVE_INTERVAL_MS = 90_000L;
    private static final long BURN_IN_ANIMATION_MS = 28_000L;
    private static final String BURN_IN_TAG = "FloatingClockBurnIn";

    private FrameLayout root;
    private TextView clockView;
    private LinearLayout infoPanel;
    private TextView dateView;
    private TextView batteryView;
    private TextView hintView;
    private CpuMonitorView cpuMonitorView;
    private Handler handler;
    private SimpleDateFormat formatter;
    private final Random random = new Random();
    private float brightness = -1f;
    private float brightnessStartY;
    private float brightnessStartValue;
    private boolean brightnessGesture;
    private boolean movedDuringTouch;
    private boolean showBattery;
    private boolean showCpu;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            updateTime();
            handler.postDelayed(this, 1000L);
        }
    };

    private final Runnable burnInMover = new Runnable() {
        @Override public void run() {
            moveClockSlightly();
            handler.postDelayed(this, BURN_IN_MOVE_INTERVAL_MS);
        }
    };

    private final Runnable batteryUpdater = new Runnable() {
        @Override public void run() {
            updateBattery();
            handler.postDelayed(this, 60_000L);
        }
    };

    private final Runnable cpuUpdater = new Runnable() {
        @Override public void run() {
            if (cpuMonitorView != null && showCpu) cpuMonitorView.sample();
            handler.postDelayed(this, 1000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handler = new Handler(Looper.getMainLooper());
        configureWindow();
        buildUi();
        applySettings();
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterImmersiveMode();
        applySettings();
        handler.removeCallbacks(burnInMover);
        handler.postDelayed(burnInMover, 10_000L);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(ticker);
        handler.removeCallbacks(burnInMover);
        handler.removeCallbacks(batteryUpdater);
        handler.removeCallbacks(cpuUpdater);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }

    private void configureWindow() {
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= 21) {
            window.setStatusBarColor(Color.BLACK);
            window.setNavigationBarColor(Color.BLACK);
        }
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        clockView = new TextView(this);
        clockView.setGravity(Gravity.CENTER);
        clockView.setIncludeFontPadding(false);
        clockView.setSingleLine(false);
        clockView.setText("12:34:56");
        clockView.setPadding(dp(18), dp(18), dp(18), dp(18));

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        root.addView(clockView, params);

        infoPanel = new LinearLayout(this);
        infoPanel.setOrientation(LinearLayout.VERTICAL);
        infoPanel.setGravity(Gravity.END);
        infoPanel.setPadding(dp(16), dp(8), dp(16), dp(8));
        FrameLayout.LayoutParams infoParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        infoParams.setMargins(0, dp(20), dp(24), 0);
        root.addView(infoPanel, infoParams);

        dateView = new TextView(this);
        dateView.setText("0000-00-00");
        dateView.setTextColor(Color.argb(210, 255, 255, 255));
        dateView.setTextSize(24);
        dateView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        dateView.setGravity(Gravity.END);
        infoPanel.addView(dateView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        batteryView = new TextView(this);
        batteryView.setText("电量 --%");
        batteryView.setTextColor(Color.argb(190, 255, 255, 255));
        batteryView.setTextSize(20);
        batteryView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        batteryView.setGravity(Gravity.END);
        infoPanel.addView(batteryView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        cpuMonitorView = new CpuMonitorView(this);
        FrameLayout.LayoutParams cpuParams = new FrameLayout.LayoutParams(dp(140), dp(258), Gravity.START | Gravity.CENTER_VERTICAL);
        cpuParams.setMargins(dp(18), 0, 0, 0);
        root.addView(cpuMonitorView, cpuParams);

        hintView = new TextView(this);
        hintView.setText("点击屏幕退出 · 左侧上下滑动调亮度 · 文字会定期移动防烧屏");
        hintView.setTextColor(Color.argb(100, 255, 255, 255));
        hintView.setTextSize(13);
        hintView.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams hintParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        hintParams.setMargins(0, 0, 0, dp(18));
        root.addView(hintView, hintParams);

        root.setOnTouchListener(this::handleRootTouch);
        setContentView(root);
    }

    private void applySettings() {
        SharedPreferences prefs = getSharedPreferences(ClockPrefs.NAME, MODE_PRIVATE);
        ClockPrefs.ensureDefaults(prefs);

        try {
            clockView.setTextColor(Color.parseColor(ClockPrefs.getDesktopTextColor(prefs)));
        } catch (IllegalArgumentException e) {
            clockView.setTextColor(Color.WHITE);
        }

        int shadow;
        try {
            shadow = Color.parseColor(ClockPrefs.getDesktopShadowColor(prefs));
        } catch (IllegalArgumentException e) {
            shadow = Color.BLACK;
        }
        clockView.setShadowLayer(10f, 4f, 4f, shadow);
        clockView.setTextSize(ClockPrefs.getDesktopTextSize(prefs));
        clockView.setTypeface(Typeface.DEFAULT, ClockPrefs.isDesktopBold(prefs) ? Typeface.BOLD : Typeface.NORMAL);
        boolean bold = ClockPrefs.isDesktopBold(prefs);
        showBattery = ClockPrefs.showDesktopBattery(prefs);
        showCpu = ClockPrefs.showDesktopCpu(prefs);
        batteryView.setVisibility(showBattery ? View.VISIBLE : View.GONE);
        cpuMonitorView.setVisibility(showCpu ? View.VISIBLE : View.GONE);
        int infoColor = clockView.getCurrentTextColor();
        dateView.setTextColor(adjustAlpha(infoColor, 210));
        batteryView.setTextColor(adjustAlpha(infoColor, 185));
        dateView.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        batteryView.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        updateDate();
        updateBattery();

        String format = ClockPrefs.getDesktopFormat(prefs);
        try {
            formatter = ClockPrefs.createFormatter(format);
        } catch (IllegalArgumentException e) {
            formatter = ClockPrefs.createFormatter(ClockPrefs.DEFAULT_DESKTOP_FORMAT);
        }

        handler.removeCallbacks(ticker);
        handler.post(ticker);
        handler.removeCallbacks(batteryUpdater);
        if (showBattery) handler.post(batteryUpdater);
        handler.removeCallbacks(cpuUpdater);
        if (showCpu) handler.post(cpuUpdater);
        clockView.post(this::moveClockSlightly);
    }

    private void updateTime() {
        if (formatter == null) formatter = ClockPrefs.createFormatter(ClockPrefs.DEFAULT_DESKTOP_FORMAT);
        clockView.setText(formatter.format(new Date()));
    }

    private void moveClockSlightly() {
        if (root == null || clockView == null) return;
        int rootW = root.getWidth();
        int rootH = root.getHeight();
        int viewW = clockView.getWidth();
        int viewH = clockView.getHeight();
        if (rootW <= 0 || rootH <= 0 || viewW <= 0 || viewH <= 0) return;

        int maxDx = Math.max(dp(8), Math.min(dp(36), (rootW - viewW) / 2 - dp(24)));
        int maxDy = Math.max(dp(8), Math.min(dp(36), (rootH - viewH) / 2 - dp(48)));
        float dx = nextNearbyOffset(clockView.getTranslationX(), maxDx, dp(10));
        float dy = nextNearbyOffset(clockView.getTranslationY(), maxDy, dp(10));
        clockView.animate().translationX(dx).translationY(dy).setDuration(BURN_IN_ANIMATION_MS).start();
        if (hintView != null) {
            hintView.animate().translationX(nextNearbyOffset(hintView.getTranslationX(), Math.max(dp(6), maxDx / 2), dp(5)))
                    .translationY(nextNearbyOffset(hintView.getTranslationY(), Math.max(dp(5), maxDy / 3), dp(4)))
                    .setDuration(BURN_IN_ANIMATION_MS)
                    .start();
        }
        if (infoPanel != null) {
            infoPanel.animate().translationX(0f)
                    .translationY(nextNearbyOffset(infoPanel.getTranslationY(), dp(42), dp(8)))
                    .setDuration(BURN_IN_ANIMATION_MS)
                    .start();
        }
        if (cpuMonitorView != null && showCpu) {
            float targetY = nextNearbyOffset(cpuMonitorView.getTranslationY(), dp(36), dp(6));
            cpuMonitorView.animate().translationX(0f)
                    .translationY(targetY)
                    .setDuration(BURN_IN_ANIMATION_MS)
                    .start();
            Log.i(BURN_IN_TAG, "CPU面板上下移动：x=0dp y=" + Math.round(targetY / getResources().getDisplayMetrics().density)
                    + "dp 时长=" + (BURN_IN_ANIMATION_MS / 1000) + "s");
        }
    }

    private void updateDate() {
        if (dateView == null) return;
        dateView.setText(new SimpleDateFormat("yyyy-MM-dd EEEE", java.util.Locale.getDefault()).format(new Date()));
    }

    private void updateBattery() {
        if (batteryView == null || !showBattery) return;
        Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery == null) {
            batteryView.setText("电量 --%");
            return;
        }
        int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        int percent = scale > 0 && level >= 0 ? Math.round(level * 100f / scale) : -1;
        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
        batteryView.setText((charging ? "⚡ " : "") + "电量 " + (percent >= 0 ? percent + "%" : "--%"));
    }

    private int adjustAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private boolean handleRootTouch(View v, MotionEvent event) {
        int leftZone = Math.max(dp(96), v.getWidth() / 5);
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                brightnessGesture = event.getX() <= leftZone;
                brightnessStartY = event.getY();
                brightnessStartValue = currentBrightness();
                movedDuringTouch = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (brightnessGesture) {
                    float delta = (brightnessStartY - event.getY()) / Math.max(1f, v.getHeight());
                    setBrightness(clamp(brightnessStartValue + delta, 0.05f, 1f));
                    movedDuringTouch = true;
                    return true;
                }
                if (Math.abs(event.getY() - brightnessStartY) > dp(12)) movedDuringTouch = true;
                return true;
            case MotionEvent.ACTION_UP:
                if (!brightnessGesture && !movedDuringTouch) finish();
                brightnessGesture = false;
                return true;
            default:
                return true;
        }
    }

    private float currentBrightness() {
        if (brightness > 0f) return brightness;
        float screenBrightness = getWindow().getAttributes().screenBrightness;
        return screenBrightness > 0f ? screenBrightness : 0.7f;
    }

    private void setBrightness(float value) {
        brightness = value;
        WindowManager.LayoutParams attrs = getWindow().getAttributes();
        attrs.screenBrightness = value;
        getWindow().setAttributes(attrs);
        if (hintView != null) {
            hintView.setText("亮度 " + Math.round(value * 100) + "% · 点击屏幕退出 · 左侧上下滑动调亮度");
        }
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private float nextNearbyOffset(float current, int max, int step) {
        if (max <= 0) return 0f;
        float next = current + randomOffset(Math.max(1, step));
        if (next > max) next = max;
        if (next < -max) next = -max;
        return next;
    }

    private float randomOffset(int max) {
        if (max <= 0) return 0f;
        return random.nextInt(max * 2 + 1) - max;
    }

    private void enterImmersiveMode() {
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
