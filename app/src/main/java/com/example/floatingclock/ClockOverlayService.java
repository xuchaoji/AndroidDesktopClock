package com.example.floatingclock;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;

public class ClockOverlayService extends Service {
    public static final String ACTION_PREFS_CHANGED = "com.example.floatingclock.PREFS_CHANGED";

    private WindowManager windowManager;
    private TextView clockView;
    private WindowManager.LayoutParams params;
    private Handler handler;
    private SharedPreferences prefs;
    private SimpleDateFormat formatter;
    private long tickDelayMillis = 1000L;
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            updateTime();
            handler.postDelayed(this, tickDelayMillis);
        }
    };

    private final BroadcastReceiver prefsReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            applySettings();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(ClockPrefs.NAME, MODE_PRIVATE);
        ClockPrefs.ensureDefaults(prefs);
        handler = new Handler(Looper.getMainLooper());
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (!canDrawOverlays()) {
            Toast.makeText(this, "没有悬浮窗权限", Toast.LENGTH_LONG).show();
            stopSelf();
            return;
        }
        createOverlay();
        registerReceiverCompat();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        applySettings();
        return START_STICKY;
    }

    private void createOverlay() {
        clockView = new TextView(this);
        clockView.setGravity(Gravity.CENTER);
        clockView.setPadding(dp(14), dp(8), dp(14), dp(8));
        clockView.setBackgroundColor(Color.TRANSPARENT);
        clockView.setIncludeFontPadding(false);
        applySettings();

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = prefs.getInt("overlay_x", dp(24));
        params.y = prefs.getInt("overlay_y", dp(80));

        clockView.setOnTouchListener(new DragTouchListener());
        windowManager.addView(clockView, params);
    }

    private void applySettings() {
        if (clockView == null) return;
        String textColor = ClockPrefs.getTextColor(prefs);
        String shadowColor = ClockPrefs.getShadowColor(prefs);
        String format = ClockPrefs.getFormat(prefs);
        try {
            clockView.setTextColor(Color.parseColor(textColor));
        } catch (IllegalArgumentException e) {
            clockView.setTextColor(Color.WHITE);
        }
        int shadow;
        try {
            shadow = Color.parseColor(shadowColor);
        } catch (IllegalArgumentException e) {
            shadow = Color.BLACK;
        }
        clockView.setTextSize(ClockPrefs.getTextSize(prefs));
        clockView.setTypeface(Typeface.DEFAULT, ClockPrefs.isBold(prefs) ? Typeface.BOLD : Typeface.NORMAL);
        clockView.setShadowLayer(6f, 2f, 2f, shadow);
        try {
            formatter = ClockPrefs.createFormatter(format);
            tickDelayMillis = ClockPrefs.usesMilliseconds(format) ? 33L : 1000L;
        } catch (IllegalArgumentException e) {
            formatter = ClockPrefs.createFormatter(ClockPrefs.DEFAULT_FORMAT);
            tickDelayMillis = 1000L;
        }
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    private void updateTime() {
        if (clockView != null) {
            if (formatter == null) formatter = ClockPrefs.createFormatter(ClockPrefs.DEFAULT_FORMAT);
            clockView.setText(formatter.format(new Date()));
        }
    }

    private boolean canDrawOverlays() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
    }

    private void registerReceiverCompat() {
        IntentFilter filter = new IntentFilter(ACTION_PREFS_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(prefsReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(prefsReceiver, filter);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        try { unregisterReceiver(prefsReceiver); } catch (Exception ignored) { }
        if (clockView != null) {
            try { windowManager.removeView(clockView); } catch (Exception ignored) { }
            clockView = null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private class DragTouchListener implements View.OnTouchListener {
        private int startX;
        private int startY;
        private float touchX;
        private float touchY;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startX = params.x;
                    startY = params.y;
                    touchX = event.getRawX();
                    touchY = event.getRawY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    params.x = startX + (int) (event.getRawX() - touchX);
                    params.y = startY + (int) (event.getRawY() - touchY);
                    windowManager.updateViewLayout(clockView, params);
                    return true;
                case MotionEvent.ACTION_UP:
                    prefs.edit().putInt("overlay_x", params.x).putInt("overlay_y", params.y).apply();
                    return true;
                default:
                    return false;
            }
        }
    }
}
