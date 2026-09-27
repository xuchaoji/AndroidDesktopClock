package com.example.floatingclock;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

/**
 * 悬浮窗 CPU 占用监控：复用桌面时钟里的 {@link CpuMonitorView} 渲染，
 * 悬浮在其它应用之上，可拖动。
 */
public class CpuOverlayService extends Service {
    public static final String TAG = "FloatingClockCpuOverlay";

    private WindowManager windowManager;
    private CpuMonitorView cpuView;
    private WindowManager.LayoutParams params;
    private Handler handler;
    private SharedPreferences prefs;

    private final Runnable sampler = new Runnable() {
        @Override public void run() {
            if (cpuView != null) cpuView.sample();
            handler.postDelayed(this, 1000L);
        }
    };

    private final BroadcastReceiver prefsReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!ClockPrefs.showCpuOverlay(prefs)) {
                stopSelf();
                return;
            }
            if (cpuView != null) cpuView.sample();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(ClockPrefs.NAME, MODE_PRIVATE);
        ClockPrefs.ensureDefaults(prefs);
        handler = new Handler(Looper.getMainLooper());
        if (!ClockPrefs.showCpuOverlay(prefs)) {
            stopSelf();
            return;
        }
        if (!canDrawOverlays()) {
            Toast.makeText(this, "没有悬浮窗权限", Toast.LENGTH_LONG).show();
            stopSelf();
            return;
        }
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createOverlay();
        registerReceiverCompat();
        handler.post(sampler);
        Log.i(TAG, "悬浮 CPU 监控已启动");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getBooleanExtra("stop", false)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!ClockPrefs.showCpuOverlay(prefs)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    private void createOverlay() {
        cpuView = new CpuMonitorView(this);
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        params = new WindowManager.LayoutParams(
                dp(140),
                dp(258),
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = prefs.getInt(ClockPrefs.KEY_CPU_OVERLAY_X, dp(16));
        params.y = prefs.getInt(ClockPrefs.KEY_CPU_OVERLAY_Y, dp(120));
        cpuView.setBackgroundColor(Color.TRANSPARENT);
        cpuView.setOnTouchListener(new DragTouchListener());
        windowManager.addView(cpuView, params);
        cpuView.sample();
    }

    private boolean canDrawOverlays() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
    }

    private void registerReceiverCompat() {
        IntentFilter filter = new IntentFilter(ClockOverlayService.ACTION_PREFS_CHANGED);
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
        if (cpuView != null) {
            try { windowManager.removeView(cpuView); } catch (Exception ignored) { }
            cpuView = null;
        }
        Log.i(TAG, "悬浮 CPU 监控已停止");
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
                    windowManager.updateViewLayout(cpuView, params);
                    return true;
                case MotionEvent.ACTION_UP:
                    prefs.edit()
                            .putInt(ClockPrefs.KEY_CPU_OVERLAY_X, params.x)
                            .putInt(ClockPrefs.KEY_CPU_OVERLAY_Y, params.y)
                            .apply();
                    return true;
                default:
                    return false;
            }
        }
    }
}
