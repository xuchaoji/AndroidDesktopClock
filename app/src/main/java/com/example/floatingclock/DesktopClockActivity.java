package com.example.floatingclock;

import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.TrafficStats;
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
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

public class DesktopClockActivity extends AppCompatActivity {
    private static final long BURN_IN_MOVE_INTERVAL_MS = 90_000L;
    private static final long BURN_IN_ANIMATION_MS = 28_000L;
    private static final String BURN_IN_TAG = "FloatingClockBurnIn";

    private FrameLayout root;
    private TextView clockView;
    private TextView dateView;
    private TextView batteryView;
    private TextView networkView;
    private WeatherCardView weatherCardView;
    private TextView hintView;
    private CpuMonitorView cpuMonitorView;
    private Handler handler;
    private SimpleDateFormat formatter;
    private final Random random = new Random();
    private float brightness = -1f;
    private float touchDownX;
    private float touchDownY;
    private float brightnessStartValue;
    private boolean brightnessGesture;
    private boolean movedDuringTouch;
    public static final String EXTRA_EDIT_MODE = "edit_mode";

    private SharedPreferences prefs;
    private boolean showDate;
    private boolean showBattery;
    private boolean showNetwork;
    private boolean showCpu;
    private boolean showWeather;
    private String cachedWeatherJson;
    private boolean editMode;
    private FrameLayout editToolbar;
    private long lastRxBytes = -1L;
    private long lastTxBytes = -1L;
    private long lastNetworkSampleMs = -1L;
    private final Runnable longPressExitRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isFinishing() && !isDestroyed()) {
                finish();
            }
        }
    };

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

    private final Runnable networkUpdater = new Runnable() {
        @Override public void run() {
            updateNetworkSpeed();
            handler.postDelayed(this, 1000L);
        }
    };

    private final Runnable cpuUpdater = new Runnable() {
        @Override public void run() {
            if (cpuMonitorView != null && showCpu) cpuMonitorView.sample();
            handler.postDelayed(this, 1000L);
        }
    };

    private final Runnable weatherUpdater = new Runnable() {
        @Override public void run() {
            if (showWeather) refreshWeather(false);
            handler.postDelayed(this, 30 * 60 * 1000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handler = new Handler(Looper.getMainLooper());
        prefs = getSharedPreferences(ClockPrefs.NAME, MODE_PRIVATE);
        ClockPrefs.ensureDefaults(prefs);
        editMode = getIntent().getBooleanExtra(EXTRA_EDIT_MODE, false);
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
        if (!editMode) handler.postDelayed(burnInMover, 10_000L);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(ticker);
        handler.removeCallbacks(burnInMover);
        handler.removeCallbacks(batteryUpdater);
        handler.removeCallbacks(networkUpdater);
        handler.removeCallbacks(cpuUpdater);
        handler.removeCallbacks(weatherUpdater);
        handler.removeCallbacks(longPressExitRunnable);
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

        dateView = new TextView(this);
        dateView.setSingleLine(true);
        dateView.setMaxLines(1);
        dateView.setText("0000-00-00");
        dateView.setTextColor(Color.argb(210, 255, 255, 255));
        dateView.setTextSize(24);
        dateView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        dateView.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        dateView.setPadding(dp(8), dp(4), dp(8), dp(4));
        FrameLayout.LayoutParams dateParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        dateParams.setMargins(0, dp(20), dp(24), 0);
        root.addView(dateView, dateParams);

        batteryView = new TextView(this);
        batteryView.setSingleLine(true);
        batteryView.setMaxLines(1);
        batteryView.setText("电量\u00A0--%");
        batteryView.setTextColor(Color.argb(190, 255, 255, 255));
        batteryView.setTextSize(20);
        batteryView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        batteryView.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        batteryView.setPadding(dp(8), dp(4), dp(8), dp(4));
        FrameLayout.LayoutParams batteryParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        batteryParams.setMargins(0, dp(58), dp(24), 0);
        root.addView(batteryView, batteryParams);

        networkView = new TextView(this);
        networkView.setText(networkSpeedText("0 B/s", "0 B/s"));
        networkView.setTextColor(Color.argb(185, 255, 255, 255));
        networkView.setTextSize(18);
        networkView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        networkView.setGravity(Gravity.END);
        networkView.setSingleLine(false);
        networkView.setLines(2);
        networkView.setIncludeFontPadding(false);
        networkView.setPadding(dp(12), dp(8), dp(12), dp(8));
        FrameLayout.LayoutParams networkParams = new FrameLayout.LayoutParams(
                dp(172),
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END);
        networkParams.setMargins(0, 0, dp(24), dp(18));
        root.addView(networkView, networkParams);

        weatherCardView = new WeatherCardView(this);
        int cardW = dp(prefs.getInt(DesktopConfig.KEY_WEATHER_WIDTH, 290));
        int cardH = dp(prefs.getInt(DesktopConfig.KEY_WEATHER_HEIGHT, 96));
        FrameLayout.LayoutParams weatherParams = new FrameLayout.LayoutParams(
                cardW, cardH, Gravity.TOP | Gravity.START);
        weatherParams.setMargins(dp(24), dp(20), 0, 0);
        root.addView(weatherCardView, weatherParams);
        weatherCardView.setOnClickListener(v -> {
            if (!editMode) cycleWeatherMode();
        });
        weatherCardView.setOnLongClickListener(v -> {
            if (!editMode) {
                Toast.makeText(this, "正在刷新天气数据...", Toast.LENGTH_SHORT).show();
                refreshWeather(true);
                return true;
            }
            return false;
        });

        cpuMonitorView = new CpuMonitorView(this);
        FrameLayout.LayoutParams cpuParams = new FrameLayout.LayoutParams(dp(140), dp(258), Gravity.START | Gravity.CENTER_VERTICAL);
        cpuParams.setMargins(dp(18), 0, 0, 0);
        root.addView(cpuMonitorView, cpuParams);

        hintView = new TextView(this);
        hintView.setText("长按屏幕退出 · 左侧上下滑动调亮度 · 文字会定期移动防烧屏");
        hintView.setTextColor(Color.argb(100, 255, 255, 255));
        hintView.setTextSize(13);
        hintView.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams hintParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        hintParams.setMargins(0, 0, 0, dp(18));
        root.addView(hintView, hintParams);

        if (editMode) buildEditToolbar();
        root.setOnTouchListener(editMode ? null : this::handleRootTouch);
        setContentView(root);
    }

    private void applySettings() {
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
        showDate = prefs.getBoolean(DesktopConfig.KEY_SHOW_DATE, true);
        showBattery = ClockPrefs.showDesktopBattery(prefs);
        showNetwork = ClockPrefs.showDesktopNetwork(prefs);
        showCpu = ClockPrefs.showDesktopCpu(prefs);
        showWeather = prefs.getBoolean(DesktopConfig.KEY_SHOW_WEATHER, true);
        dateView.setVisibility(showDate ? View.VISIBLE : View.GONE);
        batteryView.setVisibility(showBattery ? View.VISIBLE : View.GONE);
        networkView.setVisibility(showNetwork ? View.VISIBLE : View.GONE);
        cpuMonitorView.setVisibility(showCpu ? View.VISIBLE : View.GONE);
        weatherCardView.setVisibility(showWeather ? View.VISIBLE : View.GONE);
        applyTextStyle(dateView, DesktopConfig.KEY_DATE_COLOR, DesktopConfig.KEY_DATE_SIZE,
                DesktopConfig.KEY_DATE_BOLD, "#D2FFFFFF", 24, false);
        applyTextStyle(batteryView, DesktopConfig.KEY_BATTERY_COLOR, DesktopConfig.KEY_BATTERY_SIZE,
                DesktopConfig.KEY_BATTERY_BOLD, "#B9FFFFFF", 20, false);
        applyTextStyle(networkView, DesktopConfig.KEY_NETWORK_COLOR, DesktopConfig.KEY_NETWORK_SIZE,
                DesktopConfig.KEY_NETWORK_BOLD, "#B9FFFFFF", 18, true);
        weatherCardView.setPanelAlpha(prefs.getInt(DesktopConfig.KEY_WEATHER_ALPHA, 100));
        weatherCardView.setCityName(prefs.getString(DesktopConfig.KEY_WEATHER_CITY, "北京"));
        weatherCardView.setCardMode(prefs.getInt(DesktopConfig.KEY_WEATHER_MODE, WeatherCardView.MODE_DAILY));
        try {
            weatherCardView.setTextColor(Color.parseColor(prefs.getString(DesktopConfig.KEY_WEATHER_COLOR, "#D2FFFFFF")));
        } catch (Exception ignored) {
            weatherCardView.setTextColor(Color.WHITE);
        }
        weatherCardView.setTextBold(prefs.getBoolean(DesktopConfig.KEY_WEATHER_BOLD, true));
        FrameLayout.LayoutParams weatherLayout = (FrameLayout.LayoutParams) weatherCardView.getLayoutParams();
        weatherLayout.width = dp(prefs.getInt(DesktopConfig.KEY_WEATHER_WIDTH, 290));
        weatherLayout.height = dp(prefs.getInt(DesktopConfig.KEY_WEATHER_HEIGHT, 96));
        weatherCardView.setLayoutParams(weatherLayout);
        updateWeatherDisplay();
        cpuMonitorView.setPanelAlpha(prefs.getInt(DesktopConfig.KEY_CPU_ALPHA, 100));
        FrameLayout.LayoutParams cpuLayout = (FrameLayout.LayoutParams) cpuMonitorView.getLayoutParams();
        cpuLayout.width = dp(prefs.getInt(DesktopConfig.KEY_CPU_WIDTH, 140));
        cpuLayout.height = dp(prefs.getInt(DesktopConfig.KEY_CPU_HEIGHT, 258));
        cpuMonitorView.setLayoutParams(cpuLayout);
        updateDate();
        updateBattery();
        resetNetworkSample();

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
        handler.removeCallbacks(networkUpdater);
        if (showNetwork) handler.post(networkUpdater);
        handler.removeCallbacks(cpuUpdater);
        if (showCpu) handler.post(cpuUpdater);
        handler.removeCallbacks(weatherUpdater);
        if (showWeather) {
            refreshWeather(false);
            handler.postDelayed(weatherUpdater, 30 * 60 * 1000L);
        }
        root.post(() -> {
            restoreComponentPositions();
            if (editMode) {
                enableLayoutEditing();
            } else {
                root.post(this::moveClockSlightly);
            }
        });
    }

    private void updateTime() {
        if (formatter == null) formatter = ClockPrefs.createFormatter(ClockPrefs.DEFAULT_DESKTOP_FORMAT);
        clockView.setText(formatter.format(new Date()));
    }

    private void moveClockSlightly() {
        if (root == null || clockView == null) return;
        if (root.getWidth() <= 0 || root.getHeight() <= 0) return;
        animateBurnInSafely(clockView, dp(36), dp(36), dp(10));
        animateBurnInSafely(hintView, dp(18), dp(12), dp(5));
        animateBurnInSafely(dateView, dp(24), dp(24), dp(5));
        animateBurnInSafely(batteryView, dp(24), dp(24), dp(5));
        animateBurnInSafely(networkView, dp(20), dp(16), dp(5));
        animateBurnInSafely(weatherCardView, dp(20), dp(20), dp(5));
        if (cpuMonitorView != null && showCpu) {
            animateBurnInSafely(cpuMonitorView, dp(14), dp(36), dp(6));
            Log.i(BURN_IN_TAG, "CPU面板安全漂移：x="
                    + Math.round(cpuMonitorView.getTranslationX() / getResources().getDisplayMetrics().density)
                    + "dp y=" + Math.round(cpuMonitorView.getTranslationY() / getResources().getDisplayMetrics().density)
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
            batteryView.setText("电量\u00A0--%");
            return;
        }
        int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        int percent = scale > 0 && level >= 0 ? Math.round(level * 100f / scale) : -1;
        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
        String text = (charging ? "⚡ " : "") + "电量\u00A0" + (percent >= 0 ? percent + "%" : "--%");
        Log.i("FloatingClockBattery", "updateBattery: level=" + level + " scale=" + scale + " status=" + status + " -> setText: [" + text + "]");
        batteryView.setText(text);
        adjustViewBoundsIfExceeded(batteryView, DesktopConfig.COMPONENT_BATTERY);
    }

    private void adjustViewBoundsIfExceeded(View view, String component) {
        if (view == null || root == null || root.getWidth() <= 0) return;
        if (!DesktopConfig.hasPosition(prefs, component)) return;
        view.post(() -> {
            int vw = getViewWidth(view);
            int vh = getViewHeight(view);
            if (vw <= 0 || vh <= 0) return;
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) view.getLayoutParams();
            if (lp.gravity != (Gravity.TOP | Gravity.START)) return;
            float ratioX = clamp(DesktopConfig.getX(prefs, component), 0f, 1f);
            float ratioY = clamp(DesktopConfig.getY(prefs, component), 0f, 1f);
            float maxX = Math.max(0, root.getWidth() - vw);
            float maxY = Math.max(0, root.getHeight() - vh);
            float expectedX = ratioX * maxX;
            float expectedY = ratioY * maxY;
            if (Math.abs(lp.leftMargin - expectedX) > 1 || Math.abs(lp.topMargin - expectedY) > 1) {
                setBasePosition(view, expectedX, expectedY);
            }
        });
    }

    private void resetNetworkSample() {
        lastRxBytes = TrafficStats.getTotalRxBytes();
        lastTxBytes = TrafficStats.getTotalTxBytes();
        lastNetworkSampleMs = System.currentTimeMillis();
        if (networkView != null && showNetwork) networkView.setText(networkSpeedText("0 B/s", "0 B/s"));
    }

    private void updateNetworkSpeed() {
        if (networkView == null || !showNetwork) return;
        long rxBytes = TrafficStats.getTotalRxBytes();
        long txBytes = TrafficStats.getTotalTxBytes();
        long now = System.currentTimeMillis();
        if (rxBytes == TrafficStats.UNSUPPORTED || txBytes == TrafficStats.UNSUPPORTED) {
            networkView.setText(networkSpeedText("--", "--"));
            return;
        }
        if (lastRxBytes < 0L || lastTxBytes < 0L || lastNetworkSampleMs <= 0L
                || rxBytes < lastRxBytes || txBytes < lastTxBytes) {
            lastRxBytes = rxBytes;
            lastTxBytes = txBytes;
            lastNetworkSampleMs = now;
            networkView.setText(networkSpeedText("0 B/s", "0 B/s"));
            return;
        }
        long elapsedMs = Math.max(1L, now - lastNetworkSampleMs);
        double downloadBytesPerSecond = (rxBytes - lastRxBytes) * 1000d / elapsedMs;
        double uploadBytesPerSecond = (txBytes - lastTxBytes) * 1000d / elapsedMs;
        networkView.setText(networkSpeedText(
                formatSpeed(downloadBytesPerSecond),
                formatSpeed(uploadBytesPerSecond)));
        lastRxBytes = rxBytes;
        lastTxBytes = txBytes;
        lastNetworkSampleMs = now;
    }

    private String networkSpeedText(String download, String upload) {
        return String.format(Locale.getDefault(), "↓ %10s\n↑ %10s", download, upload);
    }

    private String formatSpeed(double bytesPerSecond) {
        double speed = Math.max(0d, bytesPerSecond);
        if (speed < 1024d) return String.format(Locale.getDefault(), "%.0f B/s", speed);
        speed /= 1024d;
        if (speed < 1024d) return String.format(Locale.getDefault(), speed < 10d ? "%.1f KB/s" : "%.0f KB/s", speed);
        speed /= 1024d;
        if (speed < 1024d) return String.format(Locale.getDefault(), speed < 10d ? "%.1f MB/s" : "%.0f MB/s", speed);
        speed /= 1024d;
        return String.format(Locale.getDefault(), speed < 10d ? "%.1f GB/s" : "%.0f GB/s", speed);
    }

    private void applyTextStyle(TextView view, String colorKey, String sizeKey, String boldKey,
                                String defaultColor, int defaultSize, boolean monospace) {
        try { view.setTextColor(Color.parseColor(prefs.getString(colorKey, defaultColor))); }
        catch (Exception ignored) { view.setTextColor(Color.WHITE); }
        view.setTextSize(Math.max(10, Math.min(72, prefs.getInt(sizeKey, defaultSize))));
        boolean bold = prefs.getBoolean(boldKey, true);
        view.setTypeface(monospace ? Typeface.MONOSPACE : Typeface.DEFAULT,
                bold ? Typeface.BOLD : Typeface.NORMAL);
        if (view != networkView) {
            view.setSingleLine(true);
            view.setMaxLines(1);
        }
    }

    private int adjustAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private void animateBurnInSafely(View view, int desiredMaxX, int desiredMaxY, int step) {
        if (view == null || view.getVisibility() != View.VISIBLE || root == null) return;
        if (root.getWidth() <= 0 || root.getHeight() <= 0) return;
        int vw = getViewWidth(view);
        int vh = getViewHeight(view);
        if (vw <= 0 || vh <= 0) return;

        // 如果之前的位移异常偏大（如异常越界残留），立即重置为 0，防止长时间动画漂移
        float curTransX = view.getTranslationX();
        float curTransY = view.getTranslationY();
        if (Math.abs(curTransX) > desiredMaxX * 2f) {
            view.setTranslationX(0f);
            curTransX = 0f;
        }
        if (Math.abs(curTransY) > desiredMaxY * 2f) {
            view.setTranslationY(0f);
            curTransY = 0f;
        }

        // 基准位置优先取 LayoutParams 设置的边距，兼容未完成 layout pass 的情况
        float baseLeft = baseX(view);
        float baseTop = baseY(view);
        float minDx = -baseLeft;
        float maxDx = root.getWidth() - vw - baseLeft;
        float minDy = -baseTop;
        float maxDy = root.getHeight() - vh - baseTop;

        minDx = Math.max(minDx, -desiredMaxX);
        maxDx = Math.min(maxDx, desiredMaxX);
        minDy = Math.max(minDy, -desiredMaxY);
        maxDy = Math.min(maxDy, desiredMaxY);

        if (minDx > maxDx) { minDx = 0f; maxDx = 0f; }
        if (minDy > maxDy) { minDy = 0f; maxDy = 0f; }

        float targetX = nextSafeOffset(curTransX, minDx, maxDx, step);
        float targetY = nextSafeOffset(curTransY, minDy, maxDy, step);
        view.animate().translationX(targetX).translationY(targetY)
                .setDuration(BURN_IN_ANIMATION_MS).start();
    }

    private int getViewWidth(View view) {
        if (view == null) return 0;
        if (view instanceof TextView && view != networkView) {
            TextView tv = (TextView) view;
            CharSequence cs = tv.getText();
            if (cs != null && cs.length() > 0) {
                float textW = tv.getPaint().measureText(cs.toString());
                int paddingW = tv.getCompoundPaddingLeft() + tv.getCompoundPaddingRight();
                return (int) Math.ceil(textW + paddingW);
            }
        }
        int w = view.getWidth();
        if (w > 0) return w;
        w = view.getMeasuredWidth();
        if (w > 0) return w;
        if (root != null && root.getWidth() > 0 && root.getHeight() > 0) {
            view.measure(
                    View.MeasureSpec.makeMeasureSpec(root.getWidth(), View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(root.getHeight(), View.MeasureSpec.AT_MOST));
            return view.getMeasuredWidth();
        }
        return 0;
    }

    private int getViewHeight(View view) {
        if (view == null) return 0;
        if (view instanceof TextView && view != networkView) {
            TextView tv = (TextView) view;
            Paint.FontMetrics fm = tv.getPaint().getFontMetrics();
            float textH = fm.bottom - fm.top;
            int paddingH = tv.getCompoundPaddingTop() + tv.getCompoundPaddingBottom();
            return (int) Math.ceil(textH + paddingH);
        }
        int h = view.getHeight();
        if (h > 0) return h;
        h = view.getMeasuredHeight();
        if (h > 0) return h;
        if (root != null && root.getWidth() > 0 && root.getHeight() > 0) {
            view.measure(
                    View.MeasureSpec.makeMeasureSpec(root.getWidth(), View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(root.getHeight(), View.MeasureSpec.AT_MOST));
            return view.getMeasuredHeight();
        }
        return 0;
    }

    private float nextSafeOffset(float current, float min, float max, int step) {
        if (min > max) return 0f;
        float clampedCurrent = clamp(current, min, max);
        float target = clampedCurrent + randomOffset(Math.max(1, step));
        return clamp(target, min, max);
    }

    private void buildEditToolbar() {
        editToolbar = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(220, 28, 34, 48));
        bg.setCornerRadius(dp(14));
        editToolbar.setBackground(bg);
        editToolbar.setPadding(dp(8), dp(5), dp(8), dp(5));

        Button reset = editButton("恢复默认");
        Button done = editButton("完成");
        FrameLayout.LayoutParams resetParams = new FrameLayout.LayoutParams(dp(112), dp(44), Gravity.START | Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams doneParams = new FrameLayout.LayoutParams(dp(88), dp(44), Gravity.END | Gravity.CENTER_VERTICAL);
        editToolbar.addView(reset, resetParams);
        editToolbar.addView(done, doneParams);
        FrameLayout.LayoutParams toolbarParams = new FrameLayout.LayoutParams(dp(220), dp(56), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        toolbarParams.setMargins(0, dp(10), 0, 0);
        root.addView(editToolbar, toolbarParams);
        reset.setOnClickListener(v -> resetComponentPositions());
        done.setOnClickListener(v -> {
            Toast.makeText(this, "布局已保存", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private Button editButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setBackgroundColor(Color.TRANSPARENT);
        return button;
    }

    private void enableLayoutEditing() {
        handler.removeCallbacks(burnInMover);
        hintView.setText("拖动各组件调整位置 · 完成后自动保存");
        hintView.setTextColor(Color.argb(210, 255, 220, 120));
        pinCurrentPosition(clockView);
        pinCurrentPosition(dateView);
        pinCurrentPosition(batteryView);
        pinCurrentPosition(networkView);
        pinCurrentPosition(cpuMonitorView);
        pinCurrentPosition(weatherCardView);
        attachDrag(clockView, DesktopConfig.COMPONENT_CLOCK);
        attachDrag(dateView, DesktopConfig.COMPONENT_DATE);
        attachDrag(batteryView, DesktopConfig.COMPONENT_BATTERY);
        attachDrag(networkView, DesktopConfig.COMPONENT_NETWORK);
        attachDrag(cpuMonitorView, DesktopConfig.COMPONENT_CPU);
        attachDrag(weatherCardView, DesktopConfig.COMPONENT_WEATHER);
    }

    private void pinCurrentPosition(View view) {
        if (view == null || root == null) return;
        view.animate().cancel();
        view.setTranslationX(0f);
        view.setTranslationY(0f);
        float curX = baseX(view);
        float curY = baseY(view);
        int vw = getViewWidth(view);
        int vh = getViewHeight(view);
        float maxX = Math.max(0, root.getWidth() - vw);
        float maxY = Math.max(0, root.getHeight() - vh);
        setBasePosition(view, clamp(curX, 0, maxX), clamp(curY, 0, maxY));
    }

    private void attachDrag(View view, String component) {
        if (view == null) return;
        view.animate().cancel();
        view.setTranslationX(0f);
        view.setTranslationY(0f);
        GradientDrawable outline = new GradientDrawable();
        outline.setColor(Color.argb(28, 255, 255, 255));
        outline.setStroke(dp(1), Color.argb(180, 255, 210, 80));
        outline.setCornerRadius(dp(8));
        if (view != cpuMonitorView && view != weatherCardView) view.setBackground(outline);
        view.setOnTouchListener(new View.OnTouchListener() {
            float downRawX, downRawY, startX, startY;
            @Override public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        startX = baseX(v);
                        startY = baseY(v);
                        v.animate().cancel();
                        v.setTranslationX(0f);
                        v.setTranslationY(0f);
                        v.bringToFront();
                        if (editToolbar != null) editToolbar.bringToFront();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int vw = getViewWidth(v);
                        int vh = getViewHeight(v);
                        float maxX = Math.max(0, root.getWidth() - vw);
                        float maxY = Math.max(0, root.getHeight() - vh);
                        float newX = clamp(startX + event.getRawX() - downRawX, 0, maxX);
                        float newY = clamp(startY + event.getRawY() - downRawY, 0, maxY);
                        v.setTranslationX(newX - startX);
                        v.setTranslationY(newY - startY);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        int finalVw = getViewWidth(v);
                        int finalVh = getViewHeight(v);
                        float finalMaxX = Math.max(0, root.getWidth() - finalVw);
                        float finalMaxY = Math.max(0, root.getHeight() - finalVh);
                        float finalX = clamp(startX + event.getRawX() - downRawX, 0, finalMaxX);
                        float finalY = clamp(startY + event.getRawY() - downRawY, 0, finalMaxY);
                        v.setTranslationX(0f);
                        v.setTranslationY(0f);
                        setBasePosition(v, finalX, finalY);
                        saveComponentPosition(v, component);
                        return true;
                    default: return true;
                }
            }
        });
    }

    private void setBasePosition(View view, float x, float y) {
        if (view == null) return;
        view.animate().cancel();
        view.setTranslationX(0f);
        view.setTranslationY(0f);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) view.getLayoutParams();
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.leftMargin = Math.round(x);
        lp.topMargin = Math.round(y);
        lp.rightMargin = 0;
        lp.bottomMargin = 0;
        view.setLayoutParams(lp);
    }

    private float baseX(View view) {
        if (view == null) return 0f;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) view.getLayoutParams();
        if (lp != null && lp.gravity == (Gravity.TOP | Gravity.START)) {
            return lp.leftMargin;
        }
        return view.getLeft();
    }

    private float baseY(View view) {
        if (view == null) return 0f;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) view.getLayoutParams();
        if (lp != null && lp.gravity == (Gravity.TOP | Gravity.START)) {
            return lp.topMargin;
        }
        return view.getTop();
    }

    private void saveComponentPosition(View view, String component) {
        if (view == null || root == null) return;
        int vw = getViewWidth(view);
        int vh = getViewHeight(view);
        float maxX = Math.max(1f, root.getWidth() - vw);
        float maxY = Math.max(1f, root.getHeight() - vh);
        float curX = baseX(view);
        float curY = baseY(view);
        DesktopConfig.savePosition(prefs, component,
                clamp(curX / maxX, 0f, 1f),
                clamp(curY / maxY, 0f, 1f));
    }

    private void restoreComponentPositions() {
        if (root == null || root.getWidth() <= 0 || root.getHeight() <= 0) return;
        restorePosition(clockView, DesktopConfig.COMPONENT_CLOCK);
        restorePosition(dateView, DesktopConfig.COMPONENT_DATE);
        restorePosition(batteryView, DesktopConfig.COMPONENT_BATTERY);
        restorePosition(networkView, DesktopConfig.COMPONENT_NETWORK);
        restorePosition(cpuMonitorView, DesktopConfig.COMPONENT_CPU);
        restorePosition(weatherCardView, DesktopConfig.COMPONENT_WEATHER);
        if (editToolbar != null) editToolbar.bringToFront();
    }

    private void restorePosition(View view, String component) {
        if (view == null || root == null || !DesktopConfig.hasPosition(prefs, component)) return;
        int vw = getViewWidth(view);
        int vh = getViewHeight(view);
        float maxX = Math.max(0, root.getWidth() - vw);
        float maxY = Math.max(0, root.getHeight() - vh);
        float ratioX = clamp(DesktopConfig.getX(prefs, component), 0f, 1f);
        float ratioY = clamp(DesktopConfig.getY(prefs, component), 0f, 1f);
        float targetX = clamp(ratioX * maxX, 0, maxX);
        float targetY = clamp(ratioY * maxY, 0, maxY);
        setBasePosition(view, targetX, targetY);
    }

    private void resetComponentPositions() {
        prefs.edit()
                .remove(DesktopConfig.posX(DesktopConfig.COMPONENT_CLOCK)).remove(DesktopConfig.posY(DesktopConfig.COMPONENT_CLOCK))
                .remove(DesktopConfig.posX(DesktopConfig.COMPONENT_DATE)).remove(DesktopConfig.posY(DesktopConfig.COMPONENT_DATE))
                .remove(DesktopConfig.posX(DesktopConfig.COMPONENT_BATTERY)).remove(DesktopConfig.posY(DesktopConfig.COMPONENT_BATTERY))
                .remove(DesktopConfig.posX(DesktopConfig.COMPONENT_NETWORK)).remove(DesktopConfig.posY(DesktopConfig.COMPONENT_NETWORK))
                .remove(DesktopConfig.posX(DesktopConfig.COMPONENT_CPU)).remove(DesktopConfig.posY(DesktopConfig.COMPONENT_CPU))
                .remove(DesktopConfig.posX(DesktopConfig.COMPONENT_WEATHER)).remove(DesktopConfig.posY(DesktopConfig.COMPONENT_WEATHER))
                .commit();
        recreate();
    }

    private void cycleWeatherMode() {
        if (weatherCardView == null) return;
        int currentMode = weatherCardView.getCardMode();
        int nextMode = (currentMode == WeatherCardView.MODE_DAILY) ? WeatherCardView.MODE_HOURLY : WeatherCardView.MODE_DAILY;
        weatherCardView.setCardMode(nextMode);
        prefs.edit().putInt(DesktopConfig.KEY_WEATHER_MODE, nextMode).apply();
        String modeName = (nextMode == WeatherCardView.MODE_DAILY) ? "三日对比卡片" : "逐小时走势卡片";
        Toast.makeText(this, "切换为: " + modeName, Toast.LENGTH_SHORT).show();
    }

    private void updateWeatherDisplay() {
        if (!showWeather || weatherCardView == null) return;
        int mode = prefs.getInt(DesktopConfig.KEY_WEATHER_MODE, WeatherCardView.MODE_DAILY);
        String city = prefs.getString(DesktopConfig.KEY_WEATHER_CITY, "北京");
        weatherCardView.setCityName(city);
        weatherCardView.setCardMode(mode);
        if (cachedWeatherJson != null) {
            weatherCardView.updateWeatherData(cachedWeatherJson, false);
        } else {
            String cache = prefs.getString(DesktopConfig.KEY_WEATHER_DATA_CACHE, null);
            if (cache != null) {
                cachedWeatherJson = cache;
                weatherCardView.updateWeatherData(cachedWeatherJson, false);
            } else {
                weatherCardView.updateWeatherData(null, false);
            }
        }
    }

    private void refreshWeather(boolean force) {
        if (!showWeather) return;
        float lat = prefs.getFloat(DesktopConfig.KEY_WEATHER_LAT, 39.9042f);
        float lon = prefs.getFloat(DesktopConfig.KEY_WEATHER_LON, 116.4074f);
        WeatherManager.fetchWeather(this, lat, lon, force, new WeatherManager.WeatherCallback() {
            @Override
            public void onSuccess(String json) {
                cachedWeatherJson = json;
                updateWeatherDisplay();
                if (force) Toast.makeText(DesktopClockActivity.this, "天气已更新", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String message) {
                if (cachedWeatherJson == null && weatherCardView != null) {
                    weatherCardView.updateWeatherData(null, true);
                }
                if (force) Toast.makeText(DesktopClockActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private boolean handleRootTouch(View v, MotionEvent event) {
        int leftZone = Math.max(dp(96), v.getWidth() / 5);
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                brightnessGesture = event.getX() <= leftZone;
                touchDownX = event.getX();
                touchDownY = event.getY();
                brightnessStartValue = currentBrightness();
                movedDuringTouch = false;
                if (!brightnessGesture) {
                    handler.postDelayed(longPressExitRunnable, 800L);
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (brightnessGesture) {
                    float delta = (touchDownY - event.getY()) / Math.max(1f, v.getHeight());
                    setBrightness(clamp(brightnessStartValue + delta, 0.05f, 1f));
                    movedDuringTouch = true;
                    return true;
                }
                if (Math.abs(event.getY() - touchDownY) > dp(16) || Math.abs(event.getX() - touchDownX) > dp(16)) {
                    movedDuringTouch = true;
                    handler.removeCallbacks(longPressExitRunnable);
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                handler.removeCallbacks(longPressExitRunnable);
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
            hintView.setText("亮度 " + Math.round(value * 100) + "% · 长按屏幕退出 · 左侧上下滑动调亮度");
        }
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
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
