package com.example.floatingclock;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Log;
import android.view.View;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CpuMonitorView extends View {
    private static final int HISTORY = 60;
    private static final String TAG = "FloatingClockCpu";

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<float[]> history = new ArrayList<>();
    private long[][] lastStats;
    private int coreCount;
    private int[] coreOrder;
    private long[] coreMaxFreqs;
    private boolean[] coreIsBig;
    private boolean topologyLogged;
    private String statusText = "初始化";

    public CpuMonitorView(Context context) {
        super(context);
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void sample() {
        long[][] stats = readCpuStats();
        if (stats == null || stats.length == 0) stats = readCpuFreqStats();
        if (stats == null || stats.length == 0) {
            statusText = "无数据";
            setContentDescription("CPU监控：无数据");
            invalidate();
            return;
        }
        statusText = "采样中";
        if (lastStats == null || lastStats.length != stats.length) {
            lastStats = stats;
            coreCount = stats.length;
            ensureCoreInfo();
            ensureHistory();
            invalidate();
            return;
        }
        float[] values = new float[stats.length];
        for (int i = 0; i < stats.length; i++) {
            long prevTotal = lastStats[i][0];
            long prevIdle = lastStats[i][1];
            long total = stats[i][0];
            long idle = stats[i][1];
            long totalDelta = Math.max(1, total - prevTotal);
            long idleDelta = Math.max(0, idle - prevIdle);
            values[i] = Math.max(0f, Math.min(1f, 1f - idleDelta / (float) totalDelta));
        }
        lastStats = stats;
        coreCount = stats.length;
        ensureCoreInfo();
        history.add(values);
        while (history.size() > HISTORY) history.remove(0);
        updateAccessibilitySummary(values);
        logSample(values);
        invalidate();
    }

    private void ensureHistory() {
        if (history.isEmpty() && coreCount > 0) {
            float[] zeros = new float[coreCount];
            history.add(zeros);
            updateAccessibilitySummary(zeros);
        }
    }

    private void ensureCoreInfo() {
        if (coreCount <= 0) return;
        if (coreOrder != null && coreOrder.length == coreCount && coreMaxFreqs != null && coreMaxFreqs.length == coreCount) return;
        coreMaxFreqs = new long[coreCount];
        for (int i = 0; i < coreCount; i++) {
            long max = readLongFile("/sys/devices/system/cpu/cpu" + i + "/cpufreq/cpuinfo_max_freq");
            if (max <= 0) max = readLongFile("/sys/devices/system/cpu/cpu" + i + "/cpufreq/scaling_max_freq");
            coreMaxFreqs[i] = max;
        }
        classifyBigLittle();
        coreOrder = new int[coreCount];
        for (int i = 0; i < coreCount; i++) coreOrder[i] = i;
        for (int i = 0; i < coreCount - 1; i++) {
            for (int j = i + 1; j < coreCount; j++) {
                if (coreMaxFreqs[coreOrder[j]] > coreMaxFreqs[coreOrder[i]]) {
                    int t = coreOrder[i];
                    coreOrder[i] = coreOrder[j];
                    coreOrder[j] = t;
                }
            }
        }
        logTopology();
    }

    /** 按最大频率把核心分成大核 / 小核两组，取相邻频率档位之间相对落差最大的位置作为分界。 */
    private void classifyBigLittle() {
        coreIsBig = new boolean[coreCount];
        long maxFreq = 0;
        for (long f : coreMaxFreqs) maxFreq = Math.max(maxFreq, f);
        if (maxFreq <= 0) {
            for (int i = 0; i < coreCount; i++) coreIsBig[i] = true;
            return;
        }
        List<Long> levels = new ArrayList<>();
        for (long f : coreMaxFreqs) {
            if (f <= 0 || levels.contains(f)) continue;
            levels.add(f);
        }
        for (int i = 0; i < levels.size() - 1; i++) {
            for (int j = i + 1; j < levels.size(); j++) {
                if (levels.get(j) > levels.get(i)) {
                    long t = levels.get(i);
                    levels.set(i, levels.get(j));
                    levels.set(j, t);
                }
            }
        }
        long bigThreshold = maxFreq;
        if (levels.size() >= 2) {
            double bestGap = -1d;
            int split = 0;
            for (int i = 0; i < levels.size() - 1; i++) {
                double gap = (levels.get(i) - levels.get(i + 1)) / (double) levels.get(i);
                if (gap > bestGap) {
                    bestGap = gap;
                    split = i;
                }
            }
            bigThreshold = levels.get(split);
        }
        for (int i = 0; i < coreCount; i++) {
            coreIsBig[i] = coreMaxFreqs[i] <= 0 || coreMaxFreqs[i] >= bigThreshold;
        }
    }

    private void logTopology() {
        if (topologyLogged) return;
        topologyLogged = true;
        StringBuilder big = new StringBuilder();
        StringBuilder small = new StringBuilder();
        for (int i = 0; i < coreCount; i++) {
            StringBuilder target = coreIsBig[i] ? big : small;
            if (target.length() > 0) target.append(',');
            target.append('C').append(i);
        }
        Log.i(TAG, "CPU拓扑：大核[" + big + "] 小核[" + small + "] 频率档位=" + levelsSummary());
    }

    private String levelsSummary() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < coreCount; i++) {
            if (i > 0) sb.append(' ');
            sb.append('C').append(i).append('=').append(freqText(i));
        }
        return sb.toString();
    }

    private String freqText(int core) {
        if (coreMaxFreqs == null || core < 0 || core >= coreMaxFreqs.length) return "?";
        long freq = coreMaxFreqs[core];
        if (freq <= 0) return "?";
        return String.format(Locale.US, "%.1fG", freq / 1_000_000d);
    }

    private boolean isBig(int core) {
        return coreIsBig == null || core < 0 || core >= coreIsBig.length || coreIsBig[core];
    }

    private String buildTitle() {
        if (coreCount <= 0) return "CPU";
        int bigCount = 0;
        for (int i = 0; i < coreCount; i++) if (isBig(i)) bigCount++;
        int smallCount = coreCount - bigCount;
        if (smallCount <= 0) return "CPU " + bigCount + "核";
        return "CPU " + bigCount + "大+" + smallCount + "小";
    }

    private void updateAccessibilitySummary(float[] values) {
        if (values == null || values.length == 0) {
            setContentDescription("CPU监控：无数据");
            return;
        }
        setContentDescription(buildSummary(values));
    }

    private void logSample(float[] values) {
        Log.i(TAG, buildSummary(values));
    }

    private String buildSummary(float[] values) {
        StringBuilder sb = new StringBuilder("CPU监控：");
        for (int i = 0; i < Math.min(values.length, 8); i++) {
            if (i > 0) sb.append(' ');
            sb.append('C').append(i).append('=').append(Math.round(values[i] * 100)).append('%');
        }
        return sb.toString();
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float density = getResources().getDisplayMetrics().density;
        Log.i(TAG, "面板尺寸：" + Math.round(w / density) + "dp x " + Math.round(h / density) + "dp");
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(72, 18, 24, 36));
        canvas.drawRoundRect(0, 0, w, h, dp(14), dp(14), paint);

        if (coreCount > 0) ensureCoreInfo();

        paint.setColor(Color.argb(215, 255, 255, 255));
        paint.setTextSize(dp(11));
        paint.setFakeBoldText(true);
        canvas.drawText(buildTitle(), dp(9), dp(15), paint);
        paint.setFakeBoldText(false);

        if (coreCount <= 0) {
            paint.setTextSize(dp(10));
            paint.setColor(Color.argb(160, 255, 255, 255));
            canvas.drawText(statusText, dp(9), dp(36), paint);
            return;
        }

        // 大核全宽正常大小；小核半宽、两个一行紧凑排布
        List<Integer> bigCores = new ArrayList<>();
        List<Integer> smallCores = new ArrayList<>();
        for (int i = 0; i < coreCount; i++) {
            int core = coreOrder == null ? i : coreOrder[i];
            if (isBig(core)) bigCores.add(core); else smallCores.add(core);
        }
        int smallRows = (smallCores.size() + 1) / 2;
        int totalRows = bigCores.size() + smallRows;
        if (totalRows <= 0) return;

        float left = dp(6);
        float contentW = w - dp(12);
        float top = dp(20);
        float gap = dp(4);
        float available = h - top - dp(8) - gap * Math.max(0, totalRows - 1);
        float unit = available / Math.max(1f, bigCores.size() * 2f + smallRows);
        float bigH = Math.max(dp(16), unit * 2f);
        float smallH = Math.max(dp(12), unit);

        float y = top;
        for (int core : bigCores) {
            drawCore(canvas, core, left, y, contentW, bigH, true);
            y += bigH + gap;
        }
        float smallW = (contentW - gap) / 2f;
        for (int i = 0; i < smallCores.size(); i += 2) {
            drawCore(canvas, smallCores.get(i), left, y, smallW, smallH, false);
            if (i + 1 < smallCores.size()) {
                drawCore(canvas, smallCores.get(i + 1), left + smallW + gap, y, smallW, smallH, false);
            }
            y += smallH + gap;
        }
    }

    private void drawCore(Canvas canvas, int core, float x, float y, float width, float height, boolean wide) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(42, 255, 255, 255));
        canvas.drawRoundRect(x, y, x + width, y + height, dp(6), dp(6), paint);

        float current = latestValue(core);
        paint.setTextSize(wide ? dp(9) : dp(8));
        paint.setColor(Color.argb(200, 255, 255, 255));
        // 不显示 C0/C1 这类核编号，只显示频率档位与占用率
        String label = freqText(core) + " " + Math.round(current * 100) + "%";
        canvas.drawText(label, x + dp(4), y + dp(10), paint);

        if (history.size() < 2) return;
        float graphLeft = x + dp(3);
        float graphRight = x + width - dp(3);
        float graphTop = y + dp(13);
        float graphBottom = y + height - dp(3);
        if (graphRight - graphLeft <= dp(4) || graphBottom - graphTop <= dp(3)) return;

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.3f));
        paint.setColor(colorForCore(core));
        int n = Math.min(history.size(), HISTORY);
        float lastX = graphLeft;
        float lastY = graphBottom - valueAt(0, core) * (graphBottom - graphTop);
        for (int i = 1; i < n; i++) {
            float px = graphLeft + (graphRight - graphLeft) * i / (HISTORY - 1);
            float py = graphBottom - valueAt(i, core) * (graphBottom - graphTop);
            canvas.drawLine(lastX, lastY, px, py, paint);
            lastX = px;
            lastY = py;
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private float valueAt(int historyIndex, int core) {
        if (historyIndex < 0 || historyIndex >= history.size()) return 0f;
        float[] values = history.get(historyIndex);
        return core < values.length ? values[core] : 0f;
    }

    private float latestValue(int core) {
        if (history.isEmpty()) return 0f;
        float[] values = history.get(history.size() - 1);
        return core < values.length ? values[core] : 0f;
    }

    private int colorForCore(int core) {
        int[] colors = new int[]{
                Color.rgb(92, 225, 230), Color.rgb(255, 209, 102), Color.rgb(255, 111, 145), Color.rgb(152, 245, 142),
                Color.rgb(170, 140, 255), Color.rgb(255, 159, 67), Color.rgb(72, 219, 251), Color.rgb(29, 209, 161)
        };
        return colors[core % colors.length];
    }

    private long[][] readCpuStats() {
        List<long[]> result = new ArrayList<>();
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader("/proc/stat"));
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.startsWith("cpu")) continue;
                String[] parts = line.trim().split("\\s+");
                if (parts.length < 5 || parts[0].length() <= 3) continue;
                if (parts[0].charAt(0) == 'c' && parts[0].charAt(1) == 'p' && parts[0].charAt(2) == 'u' && Character.isDigit(parts[0].charAt(3))) {
                    long user = parse(parts, 1);
                    long nice = parse(parts, 2);
                    long system = parse(parts, 3);
                    long idle = parse(parts, 4);
                    long iowait = parse(parts, 5);
                    long irq = parse(parts, 6);
                    long softirq = parse(parts, 7);
                    long steal = parse(parts, 8);
                    long idleAll = idle + iowait;
                    long total = user + nice + system + idle + iowait + irq + softirq + steal;
                    result.add(new long[]{total, idleAll});
                }
            }
        } catch (Throwable ignored) {
            return null;
        } finally {
            try { if (br != null) br.close(); } catch (Exception ignored) { }
        }
        return result.toArray(new long[result.size()][]);
    }

    private long[][] readCpuFreqStats() {
        int count = Runtime.getRuntime().availableProcessors();
        if (count <= 0) return null;
        long[][] result = new long[count][];
        long now = System.currentTimeMillis();
        int valid = 0;
        for (int i = 0; i < count; i++) {
            long cur = readLongFile("/sys/devices/system/cpu/cpu" + i + "/cpufreq/scaling_cur_freq");
            long max = readLongFile("/sys/devices/system/cpu/cpu" + i + "/cpufreq/cpuinfo_max_freq");
            long busy = 0L;
            if (cur > 0 && max > 0) {
                busy = Math.max(0, Math.min(1000, cur * 1000 / max));
                valid++;
            }
            result[i] = new long[]{now + 1000, 1000 - busy};
        }
        return valid > 0 ? result : null;
    }

    private long readLongFile(String path) {
        BufferedReader br = null;
        try {
            File file = new File(path);
            if (!file.exists()) return 0L;
            br = new BufferedReader(new FileReader(file));
            String line = br.readLine();
            return line == null ? 0L : Long.parseLong(line.trim());
        } catch (Throwable ignored) {
            return 0L;
        } finally {
            try { if (br != null) br.close(); } catch (Exception ignored) { }
        }
    }

    private long parse(String[] parts, int index) {
        if (index >= parts.length) return 0L;
        try { return Long.parseLong(parts[index]); } catch (Exception e) { return 0L; }
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
