package com.example.floatingclock;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 结构化天气卡片组件（参考表格卡片格式）：
 * 支持【三日对比卡片】与【逐小时卡片】点击切换。
 *
 * 格式参考：
 * |日期|10/1 (昨)|10/2 (今)|10/3 (明)|
 * |天气|  阴     |  多云   |  小雨   |
 * |温度|23°~11°  |21°~11°  |22°~11°  |
 */
public class WeatherCardView extends View {
    public static final int MODE_DAILY = 0;   // 三日对比卡片
    public static final int MODE_HOURLY = 1;  // 逐小时走势卡片

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rectF = new RectF();

    private int cardMode = MODE_DAILY;
    private int panelAlphaPercent = 100;
    private int customTextColor = Color.WHITE;
    private boolean customBold = true;

    private String cityName = "北京";
    private String currentSummary = "天气加载中...";
    private String rawJsonData;
    private boolean isOffline = false;

    // 解析后的每日数据
    private final List<DailyItem> dailyItems = new ArrayList<>();
    // 解析后的逐小时数据
    private final List<HourlyItem> hourlyItems = new ArrayList<>();

    public static class DailyItem {
        public String dateStr;    // "10/1"
        public String sublabel;   // "昨", "今", "明"
        public String weatherDesc;// "晴"
        public String icon;       // "☀️"
        public int maxTemp;       // 23
        public int minTemp;       // 11
        public boolean isToday;
    }

    public static class HourlyItem {
        public String timeStr;    // "12:00"
        public String sublabel;   // "一小时前", "现在", "+1h"
        public String weatherDesc;// "多云"
        public String icon;       // "⛅"
        public int temp;          // 20
        public boolean isNow;
    }

    public interface OnModeChangeListener {
        void onModeChanged(int newMode);
    }

    private OnModeChangeListener modeChangeListener;

    public WeatherCardView(Context context) {
        super(context);
        init();
    }

    public WeatherCardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setClickable(true);
        setFocusable(true);
    }

    public void setOnModeChangeListener(OnModeChangeListener listener) {
        this.modeChangeListener = listener;
    }

    public int getCardMode() {
        return cardMode;
    }

    public void setCardMode(int mode) {
        if (this.cardMode != mode) {
            this.cardMode = mode;
            invalidate();
            if (modeChangeListener != null) {
                modeChangeListener.onModeChanged(mode);
            }
        }
    }

    public void toggleMode() {
        setCardMode(cardMode == MODE_DAILY ? MODE_HOURLY : MODE_DAILY);
    }

    public void setPanelAlpha(int percent) {
        this.panelAlphaPercent = Math.max(10, Math.min(100, percent));
        invalidate();
    }

    public void setTextColor(int color) {
        this.customTextColor = color;
        invalidate();
    }

    public void setTextBold(boolean bold) {
        this.customBold = bold;
        invalidate();
    }

    public void setCityName(String name) {
        this.cityName = (name != null && !name.trim().isEmpty()) ? name.trim() : "本地";
        invalidate();
    }

    /**
     * 更新天气数据并解析成卡片表格数据
     */
    public void updateWeatherData(String jsonStr, boolean offline) {
        this.rawJsonData = jsonStr;
        this.isOffline = offline;
        parseJson(jsonStr);
        invalidate();
    }

    private void parseJson(String jsonStr) {
        dailyItems.clear();
        hourlyItems.clear();
        if (jsonStr == null || jsonStr.trim().isEmpty()) {
            currentSummary = cityName + " · 等待拉取天气";
            return;
        }

        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONObject current = root.optJSONObject("current");
            JSONObject daily = root.optJSONObject("daily");
            JSONObject hourly = root.optJSONObject("hourly");

            // 1. 顶部摘要解析
            if (current != null) {
                double temp = current.optDouble("temperature_2m", 0);
                double appTemp = current.optDouble("apparent_temperature", temp);
                int hum = current.optInt("relative_humidity_2m", 0);
                int code = current.optInt("weather_code", 0);
                double wind = current.optDouble("wind_speed_10m", 0);

                currentSummary = String.format(Locale.getDefault(),
                        "📍 %s · %s %.0f℃ %s · 体感 %.0f℃ · 湿度 %d%%",
                        cityName, WeatherManager.getWeatherIcon(code), temp,
                        WeatherManager.getWeatherDesc(code), appTemp, hum);
            } else {
                currentSummary = "📍 " + cityName + " · 天气概况";
            }

            // 2. 三日表格数据解析 (昨、今、明)
            if (daily != null) {
                JSONArray timeArr = daily.optJSONArray("time");
                JSONArray maxArr = daily.optJSONArray("temperature_2m_max");
                JSONArray minArr = daily.optJSONArray("temperature_2m_min");
                JSONArray codeArr = daily.optJSONArray("weather_code");

                String[] sublabels = new String[]{"昨", "今", "明", "后"};
                int count = (timeArr != null) ? Math.min(3, timeArr.length()) : 0;
                for (int i = 0; i < count; i++) {
                    DailyItem item = new DailyItem();
                    String tStr = timeArr.optString(i, "");
                    item.dateStr = formatMonthDay(tStr);
                    item.sublabel = (i < sublabels.length) ? sublabels[i] : "";
                    item.isToday = (i == 1);
                    int code = (codeArr != null) ? codeArr.optInt(i, 0) : 0;
                    item.weatherDesc = WeatherManager.getWeatherDesc(code);
                    item.icon = WeatherManager.getWeatherIcon(code);
                    item.maxTemp = (maxArr != null) ? (int) Math.round(maxArr.optDouble(i, 0)) : 0;
                    item.minTemp = (minArr != null) ? (int) Math.round(minArr.optDouble(i, 0)) : 0;
                    dailyItems.add(item);
                }
            }

            // 3. 逐小时表格数据解析 (前一小时、现在、未来三小时)
            if (hourly != null) {
                JSONArray hTime = hourly.optJSONArray("time");
                JSONArray hTemp = hourly.optJSONArray("temperature_2m");
                JSONArray hCode = hourly.optJSONArray("weather_code");

                if (hTime != null && hTemp != null && hTime.length() >= 16) {
                    // 默认 past_hours=12, 当前小时在 index=12
                    int nowIdx = 12;
                    String currTime = (current != null) ? current.optString("time", "") : "";
                    if (currTime.length() >= 13) {
                        String currPrefix = currTime.substring(0, 13);
                        for (int k = 0; k < hTime.length(); k++) {
                            if (hTime.optString(k, "").startsWith(currPrefix)) {
                                nowIdx = k;
                                break;
                            }
                        }
                    }

                    int[] offsets = new int[]{-1, 0, 1, 2, 3};
                    String[] subs = new String[]{"一小时前", "现在", "+1h", "+2h", "+3h"};
                    for (int o = 0; o < offsets.length; o++) {
                        int idx = nowIdx + offsets[o];
                        if (idx >= 0 && idx < hTime.length()) {
                            HourlyItem item = new HourlyItem();
                            String fullT = hTime.optString(idx, "");
                            item.timeStr = extractHourMinute(fullT);
                            item.sublabel = subs[o];
                            item.isNow = (offsets[o] == 0);
                            int c = (hCode != null) ? hCode.optInt(idx, 0) : 0;
                            item.weatherDesc = WeatherManager.getWeatherDesc(c);
                            item.icon = WeatherManager.getWeatherIcon(c);
                            item.temp = (int) Math.round(hTemp.optDouble(idx, 0));
                            hourlyItems.add(item);
                        }
                    }
                }
            }

        } catch (Exception e) {
            currentSummary = cityName + " · 数据解析异常 (点击刷新)";
        }
    }

    private String formatMonthDay(String dateStr) {
        if (dateStr == null || dateStr.length() < 10) return dateStr;
        try {
            String[] parts = dateStr.split("-");
            if (parts.length >= 3) {
                int m = Integer.parseInt(parts[1]);
                int d = Integer.parseInt(parts[2]);
                return m + "/" + d;
            }
        } catch (Exception ignored) { }
        return dateStr;
    }

    private String extractHourMinute(String isoTime) {
        if (isoTime == null) return "--:--";
        int tIdx = isoTime.indexOf('T');
        if (tIdx >= 0 && tIdx + 6 <= isoTime.length()) {
            return isoTime.substring(tIdx + 1, tIdx + 6);
        }
        return isoTime;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float density = getResources().getDisplayMetrics().density;
        int defWidth = Math.round(360 * density);
        int defHeight = Math.round(168 * density);

        int width = resolveSize(defWidth, widthMeasureSpec);
        int height = resolveSize(defHeight, heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float density = getResources().getDisplayMetrics().density;
        float cornerRadius = 14 * density;

        // 1. 卡片外框与半透明毛玻璃背景
        int alpha255 = Math.round(255 * (panelAlphaPercent / 100f));
        rectF.set(0, 0, w, h);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(Math.round(alpha255 * 0.88f), 15, 20, 32));
        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint);

        // 卡片边框
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.2f * density);
        paint.setColor(Color.argb(Math.round(alpha255 * 0.22f), 255, 255, 255));
        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint);

        // 2. 顶部栏 (Header)
        float headerH = 34 * density;
        drawHeader(canvas, w, headerH, density, alpha255);

        // 顶部栏与表格之间的水平分割线
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f * density);
        paint.setColor(Color.argb(Math.round(alpha255 * 0.18f), 255, 255, 255));
        canvas.drawLine(8 * density, headerH, w - 8 * density, headerH, paint);

        // 3. 表格区域
        float tableTop = headerH + 4 * density;
        float tableBottom = h - 6 * density;
        float tableLeft = 8 * density;
        float tableRight = w - 8 * density;

        if (cardMode == MODE_DAILY) {
            drawDailyTable(canvas, tableLeft, tableTop, tableRight, tableBottom, density, alpha255);
        } else {
            drawHourlyTable(canvas, tableLeft, tableTop, tableRight, tableBottom, density, alpha255);
        }
    }

    private void drawHeader(Canvas canvas, int w, float headerH, float density, int alpha255) {
        paint.setStyle(Paint.Style.FILL);
        paint.setFakeBoldText(true);
        paint.setTextSize(12.5f * density);
        paint.setColor(Color.argb(alpha255, 240, 245, 255));

        // 左侧实时概况文本
        Paint.FontMetrics fm = paint.getFontMetrics();
        float textY = (headerH - (fm.bottom + fm.top)) / 2f;
        canvas.drawText(currentSummary, 12 * density, textY, paint);

        // 右侧模式指示器胶囊
        String badgeText = (cardMode == MODE_DAILY) ? "📅 三日卡片" : "⏱️ 逐小时卡片";
        paint.setTextSize(11f * density);
        float badgeTextW = paint.measureText(badgeText);
        float badgePadH = 7 * density;
        float badgeW = badgeTextW + badgePadH * 2;
        float badgeH = 20 * density;
        float badgeRight = w - 10 * density;
        float badgeLeft = badgeRight - badgeW;
        float badgeTop = (headerH - badgeH) / 2f;

        RectF badgeRect = new RectF(badgeLeft, badgeTop, badgeRight, badgeTop + badgeH);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(Math.round(alpha255 * 0.22f), 66, 183, 255));
        canvas.drawRoundRect(badgeRect, 10 * density, 10 * density, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f * density);
        paint.setColor(Color.argb(Math.round(alpha255 * 0.5f), 100, 210, 255));
        canvas.drawRoundRect(badgeRect, 10 * density, 10 * density, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(alpha255, 215, 240, 255));
        Paint.FontMetrics bFm = paint.getFontMetrics();
        float bTextY = badgeTop + (badgeH - (bFm.bottom + bFm.top)) / 2f;
        canvas.drawText(badgeText, badgeLeft + badgePadH, bTextY, paint);
    }

    /**
     * 绘制三日对比表格:
     * |日期|10/1(昨)|10/2(今)|10/3(明)|
     * |天气|   阴   |  多云  |  小雨  |
     * |温度| 23°~11°| 21°~11°| 22°~11°|
     */
    private void drawDailyTable(Canvas canvas, float left, float top, float right, float bottom, float density, int alpha255) {
        float tableW = right - left;
        float tableH = bottom - top;

        // 列宽划分：左侧标题列占 20%，3个数据列各占约 26.6%
        float headerColW = tableW * 0.20f;
        float dataColW = (tableW - headerColW) / 3f;

        // 行高划分
        float row0H = tableH * 0.32f; // 日期行
        float row1H = tableH * 0.30f; // 天气行
        float row2H = tableH - row0H - row1H; // 温度行

        float r0Bottom = top + row0H;
        float r1Bottom = r0Bottom + row1H;

        // 1. 标题列浅色背景
        rectF.set(left, top, left + headerColW, bottom);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(Math.round(alpha255 * 0.12f), 255, 255, 255));
        canvas.drawRoundRect(rectF, 4 * density, 4 * density, paint);

        // 2. 高亮“今天”所在的列
        if (dailyItems.size() >= 2) {
            float todayLeft = left + headerColW + dataColW;
            float todayRight = todayLeft + dataColW;
            rectF.set(todayLeft, top, todayRight, bottom);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(Math.round(alpha255 * 0.16f), 66, 183, 255));
            canvas.drawRoundRect(rectF, 6 * density, 6 * density, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.2f * density);
            paint.setColor(Color.argb(Math.round(alpha255 * 0.45f), 100, 210, 255));
            canvas.drawRoundRect(rectF, 6 * density, 6 * density, paint);
        }

        // 3. 网格水平分隔线
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f * density);
        paint.setColor(Color.argb(Math.round(alpha255 * 0.18f), 255, 255, 255));
        canvas.drawLine(left, r0Bottom, right, r0Bottom, paint);
        canvas.drawLine(left, r1Bottom, right, r1Bottom, paint);

        // 4. 网格垂直分隔线
        for (int i = 0; i <= 3; i++) {
            float x = left + headerColW + i * dataColW;
            canvas.drawLine(x, top, x, bottom, paint);
        }

        // 5. 绘制标题列文字【日期、天气、温度】
        paint.setStyle(Paint.Style.FILL);
        paint.setFakeBoldText(customBold);
        paint.setTextSize(13f * density);
        paint.setColor(Color.argb(Math.round(alpha255 * 0.85f), 220, 230, 250));

        drawCenteredText(canvas, "日期", left, top, headerColW, row0H);
        drawCenteredText(canvas, "天气", left, r0Bottom, headerColW, row1H);
        drawCenteredText(canvas, "温度", left, r1Bottom, headerColW, row2H);

        // 6. 绘制各日数据
        for (int i = 0; i < Math.min(3, dailyItems.size()); i++) {
            DailyItem item = dailyItems.get(i);
            float colX = left + headerColW + i * dataColW;

            // Row 0: 日期 + (昨/今/明)
            float cy0 = top + row0H / 2f;
            paint.setTextSize(12.5f * density);
            paint.setFakeBoldText(true);
            paint.setColor(item.isToday ? Color.argb(alpha255, 120, 220, 255) : Color.argb(alpha255, 240, 245, 255));
            drawTextCenteredAt(canvas, item.dateStr, colX + dataColW / 2f, cy0 - 7 * density);

            paint.setTextSize(11f * density);
            paint.setFakeBoldText(false);
            paint.setColor(item.isToday ? Color.argb(alpha255, 255, 215, 100) : Color.argb(Math.round(alpha255 * 0.7f), 200, 210, 225));
            drawTextCenteredAt(canvas, item.sublabel, colX + dataColW / 2f, cy0 + 8 * density);

            // Row 1: 天气图标 + 描述
            float cy1 = r0Bottom + row1H / 2f;
            paint.setTextSize(13f * density);
            paint.setFakeBoldText(false);
            paint.setColor(Color.argb(alpha255, 245, 245, 255));
            String weatherLabel = item.icon + " " + item.weatherDesc;
            drawTextCenteredAt(canvas, weatherLabel, colX + dataColW / 2f, cy1);

            // Row 2: 温度 (xx° ~ xx°)
            // 采用上下堆叠呈现：高温 / ~ / 低温
            float cy2 = r1Bottom + row2H / 2f;
            paint.setTextSize(12.5f * density);
            paint.setFakeBoldText(true);
            paint.setColor(Color.argb(alpha255, 255, 175, 90)); // 高温橙色
            drawTextCenteredAt(canvas, item.maxTemp + "°", colX + dataColW / 2f, cy2 - 9 * density);

            paint.setTextSize(9.5f * density);
            paint.setColor(Color.argb(Math.round(alpha255 * 0.6f), 200, 210, 225));
            drawTextCenteredAt(canvas, "~", colX + dataColW / 2f, cy2);

            paint.setTextSize(12.5f * density);
            paint.setColor(Color.argb(alpha255, 110, 205, 255)); // 低温浅蓝
            drawTextCenteredAt(canvas, item.minTemp + "°", colX + dataColW / 2f, cy2 + 9 * density);
        }
    }

    /**
     * 绘制逐小时表格:
     * |时间|12:00(一小时前)|13:00(现在)|14:00|15:00|16:00|
     * |天气|     阴        |   多云    |小雨 | 小雨| 小雨|
     * |温度|    20°        |    21°    | 21° |  19°|  17°|
     */
    private void drawHourlyTable(Canvas canvas, float left, float top, float right, float bottom, float density, int alpha255) {
        float tableW = right - left;
        float tableH = bottom - top;

        // 列宽划分：左侧标题列占 16%，5个逐小时数据列平分剩余 84%
        float headerColW = tableW * 0.16f;
        float dataColW = (tableW - headerColW) / 5f;

        float row0H = tableH * 0.32f;
        float row1H = tableH * 0.30f;
        float row2H = tableH - row0H - row1H;

        float r0Bottom = top + row0H;
        float r1Bottom = r0Bottom + row1H;

        // 1. 标题列背景
        rectF.set(left, top, left + headerColW, bottom);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(Math.round(alpha255 * 0.12f), 255, 255, 255));
        canvas.drawRoundRect(rectF, 4 * density, 4 * density, paint);

        // 2. 高亮“现在”所在的列 (index 1)
        if (hourlyItems.size() >= 2) {
            float nowLeft = left + headerColW + 1 * dataColW;
            float nowRight = nowLeft + dataColW;
            rectF.set(nowLeft, top, nowRight, bottom);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(Math.round(alpha255 * 0.16f), 66, 183, 255));
            canvas.drawRoundRect(rectF, 6 * density, 6 * density, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.2f * density);
            paint.setColor(Color.argb(Math.round(alpha255 * 0.45f), 100, 210, 255));
            canvas.drawRoundRect(rectF, 6 * density, 6 * density, paint);
        }

        // 3. 水平分隔线
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f * density);
        paint.setColor(Color.argb(Math.round(alpha255 * 0.18f), 255, 255, 255));
        canvas.drawLine(left, r0Bottom, right, r0Bottom, paint);
        canvas.drawLine(left, r1Bottom, right, r1Bottom, paint);

        // 4. 垂直分隔线
        for (int i = 0; i <= 5; i++) {
            float x = left + headerColW + i * dataColW;
            canvas.drawLine(x, top, x, bottom, paint);
        }

        // 5. 绘制标题列【时间、天气、温度】
        paint.setStyle(Paint.Style.FILL);
        paint.setFakeBoldText(customBold);
        paint.setTextSize(13f * density);
        paint.setColor(Color.argb(Math.round(alpha255 * 0.85f), 220, 230, 250));

        drawCenteredText(canvas, "时间", left, top, headerColW, row0H);
        drawCenteredText(canvas, "天气", left, r0Bottom, headerColW, row1H);
        drawCenteredText(canvas, "温度", left, r1Bottom, headerColW, row2H);

        // 6. 绘制逐小时列数据
        for (int i = 0; i < Math.min(5, hourlyItems.size()); i++) {
            HourlyItem item = hourlyItems.get(i);
            float colX = left + headerColW + i * dataColW;

            // Row 0: 时间 + (一小时前 / 现在 / +1h...)
            float cy0 = top + row0H / 2f;
            paint.setTextSize(11.5f * density);
            paint.setFakeBoldText(true);
            paint.setColor(item.isNow ? Color.argb(alpha255, 120, 220, 255) : Color.argb(alpha255, 240, 245, 255));
            drawTextCenteredAt(canvas, item.timeStr, colX + dataColW / 2f, cy0 - 6.5f * density);

            paint.setTextSize(10f * density);
            paint.setFakeBoldText(false);
            paint.setColor(item.isNow ? Color.argb(alpha255, 255, 215, 100) : Color.argb(Math.round(alpha255 * 0.65f), 190, 205, 225));
            drawTextCenteredAt(canvas, item.sublabel, colX + dataColW / 2f, cy0 + 7.5f * density);

            // Row 1: 天气图标 + 简短天气
            float cy1 = r0Bottom + row1H / 2f;
            paint.setTextSize(11.5f * density);
            paint.setColor(Color.argb(alpha255, 245, 245, 255));
            drawTextCenteredAt(canvas, item.icon + item.weatherDesc, colX + dataColW / 2f, cy1);

            // Row 2: 小时温度
            float cy2 = r1Bottom + row2H / 2f;
            paint.setTextSize(14f * density);
            paint.setFakeBoldText(true);
            paint.setColor(item.isNow ? Color.argb(alpha255, 255, 215, 100) : Color.argb(alpha255, 120, 220, 255));
            drawTextCenteredAt(canvas, item.temp + "°", colX + dataColW / 2f, cy2);
        }
    }

    private void drawCenteredText(Canvas canvas, String text, float cellX, float cellY, float cellW, float cellH) {
        Paint.FontMetrics fm = paint.getFontMetrics();
        float textY = cellY + (cellH - (fm.bottom + fm.top)) / 2f;
        float textW = paint.measureText(text);
        float textX = cellX + (cellW - textW) / 2f;
        canvas.drawText(text, textX, textY, paint);
    }

    private void drawTextCenteredAt(Canvas canvas, String text, float centerX, float centerY) {
        if (text == null || text.isEmpty()) return;
        Paint.FontMetrics fm = paint.getFontMetrics();
        float baseline = centerY - (fm.bottom + fm.top) / 2f;
        float w = paint.measureText(text);
        canvas.drawText(text, centerX - w / 2f, baseline, paint);
    }
}
