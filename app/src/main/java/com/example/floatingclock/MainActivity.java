package com.example.floatingclock;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.app.AlertDialog;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private static final int REQ_OVERLAY = 1001;

    private EditText textColorEdit;
    private EditText shadowColorEdit;
    private EditText formatEdit;
    private SeekBar sizeSeek;
    private TextView sizeLabel;
    private CheckBox boldCheck;

    private EditText desktopTextColorEdit;
    private EditText desktopShadowColorEdit;
    private EditText desktopFormatEdit;
    private SeekBar desktopSizeSeek;
    private TextView desktopSizeLabel;
    private CheckBox desktopBoldCheck;
    private CheckBox desktopBatteryCheck;
    private CheckBox desktopNetworkCheck;
    private CheckBox desktopCpuCheck;
    private CheckBox cpuOverlayCheck;
    private CheckBox desktopDateCheck;
    private EditText dateColorEdit;
    private EditText batteryColorEdit;
    private EditText networkColorEdit;
    private SeekBar dateSizeSeek;
    private SeekBar batterySizeSeek;
    private SeekBar networkSizeSeek;
    private SeekBar cpuWidthSeek;
    private SeekBar cpuHeightSeek;
    private SeekBar cpuAlphaSeek;
    private CheckBox dateBoldCheck;
    private CheckBox batteryBoldCheck;
    private CheckBox networkBoldCheck;
    private CheckBox desktopWeatherCheck;
    private EditText weatherCityEdit;
    private Spinner weatherModeSpinner;
    private EditText weatherColorEdit;
    private SeekBar weatherSizeSeek;
    private SeekBar weatherWidthSeek;
    private SeekBar weatherHeightSeek;
    private SeekBar weatherAlphaSeek;
    private CheckBox weatherBoldCheck;
    private Spinner desktopPresetSpinner;
    private ArrayAdapter<String> desktopPresetAdapter;

    private TextView preview;
    private ScrollView scrollView;
    private LinearLayout floatingSection;
    private LinearLayout desktopSection;
    private Button floatingTab;
    private Button desktopTab;
    private SharedPreferences prefs;
    private boolean showingDesktopTab = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(ClockPrefs.NAME, MODE_PRIVATE);
        ClockPrefs.ensureDefaults(prefs);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePreview();
        // 回到设置页时按开关状态对齐悬浮 CPU 监控（例如重启后自愈）
        syncCpuOverlay(false);
    }

    private void buildUi() {
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(Color.rgb(246, 248, 255));

        scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(18));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        screen.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView title = new TextView(this);
        title.setText("⏰ 时钟中心");
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(Color.rgb(34, 48, 92));
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title, matchWrap());

        preview = new TextView(this);
        preview.setText("12:34:56");
        preview.setGravity(Gravity.CENTER);
        preview.setPadding(dp(14), dp(14), dp(14), dp(14));
        preview.setMaxHeight(dp(260));
        preview.setBackground(cardBg(Color.rgb(35, 39, 58), 20));
        LinearLayout.LayoutParams previewParams = matchWrap();
        previewParams.setMargins(0, dp(12), 0, dp(8));
        root.addView(preview, previewParams);

        LinearLayout quickButtons = new LinearLayout(this);
        quickButtons.setOrientation(LinearLayout.HORIZONTAL);
        quickButtons.setPadding(0, dp(8), 0, dp(4));
        root.addView(quickButtons, matchWrap());

        Button startButton = primaryButton("启动悬浮");
        quickButtons.addView(startButton, weightWrap(1f));

        Button stopButton = secondaryButton("停止悬浮");
        quickButtons.addView(stopButton, weightWrap(1f));

        Button desktopButton = secondaryButton("桌面时钟");
        quickButtons.addView(desktopButton, weightWrap(1f));

        LinearLayout assistButtons = new LinearLayout(this);
        assistButtons.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(assistButtons, matchWrap());

        Button permissionButton = secondaryButton("悬浮窗权限");
        assistButtons.addView(permissionButton, weightWrap(1f));

        Button saveButton = primaryButton("保存全部");
        assistButtons.addView(saveButton, weightWrap(1f));

        floatingSection = new LinearLayout(this);
        floatingSection.setOrientation(LinearLayout.VERTICAL);
        floatingSection.setPadding(dp(14), dp(12), dp(14), dp(14));
        floatingSection.setBackground(cardBg(Color.WHITE, 18));
        LinearLayout.LayoutParams floatingCardParams = matchWrap();
        floatingCardParams.setMargins(0, dp(12), 0, dp(12));
        root.addView(floatingSection, floatingCardParams);
        buildFloatingSection(floatingSection);

        desktopSection = new LinearLayout(this);
        desktopSection.setOrientation(LinearLayout.VERTICAL);
        desktopSection.setPadding(dp(14), dp(12), dp(14), dp(14));
        desktopSection.setBackground(cardBg(Color.WHITE, 18));
        LinearLayout.LayoutParams desktopCardParams = matchWrap();
        desktopCardParams.setMargins(0, dp(12), 0, dp(12));
        root.addView(desktopSection, desktopCardParams);
        buildDesktopSection(desktopSection);

        LinearLayout bottomTabs = new LinearLayout(this);
        bottomTabs.setOrientation(LinearLayout.HORIZONTAL);
        bottomTabs.setPadding(dp(12), dp(6), dp(12), dp(8));
        bottomTabs.setBackgroundColor(Color.WHITE);
        screen.addView(bottomTabs, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        floatingTab = secondaryButton("悬浮时钟");
        bottomTabs.addView(floatingTab, weightWrap(1f));

        desktopTab = secondaryButton("桌面时钟");
        bottomTabs.addView(desktopTab, weightWrap(1f));

        permissionButton.setOnClickListener(v -> requestOverlayPermission());
        saveButton.setOnClickListener(v -> saveAllSettings(true));
        startButton.setOnClickListener(v -> { if (saveAllSettings(false)) startClock(); });
        stopButton.setOnClickListener(v -> stopService(new Intent(this, ClockOverlayService.class)));
        desktopButton.setOnClickListener(v -> { if (saveAllSettings(false)) startActivity(new Intent(this, DesktopClockActivity.class)); });
        floatingTab.setOnClickListener(v -> showTab(false));
        desktopTab.setOnClickListener(v -> showTab(true));

        setContentView(screen);
        showTab(false);
        updatePreview();
    }

    private void buildFloatingSection(LinearLayout root) {
        TextView tip = sectionTip("悬浮时钟支持毫秒格式，格式含 S 会启用毫秒刷新。悬浮文字可拖动。");
        root.addView(tip, matchWrap());
        textColorEdit = addLabeledEdit(root, "文字颜色", ClockPrefs.getTextColor(prefs), InputType.TYPE_CLASS_TEXT);
        Button pickTextColorButton = secondaryButton("选择文字颜色");
        root.addView(pickTextColorButton, matchWrap());

        shadowColorEdit = addLabeledEdit(root, "阴影颜色", ClockPrefs.getShadowColor(prefs), InputType.TYPE_CLASS_TEXT);
        Button pickShadowColorButton = secondaryButton("选择阴影颜色");
        root.addView(pickShadowColorButton, matchWrap());

        sizeLabel = label("字体大小");
        root.addView(sizeLabel, matchWrap());
        sizeSeek = new SeekBar(this);
        sizeSeek.setMax(72 - 12);
        sizeSeek.setProgress(ClockPrefs.getTextSize(prefs) - 12);
        root.addView(sizeSeek, matchWrap());

        boldCheck = new CheckBox(this);
        boldCheck.setText("粗体显示");
        boldCheck.setTextColor(Color.rgb(24, 32, 56));
        boldCheck.setChecked(ClockPrefs.isBold(prefs));
        root.addView(boldCheck, matchWrap());

        TextView presetLabel = label("常用时间格式");
        root.addView(presetLabel, matchWrap());
        Spinner spinner = new Spinner(this);
        String[] presets = new String[]{"不使用预设", "HH:mm:ss.SSS", "HH:mm:ss", "HH:mm", "yyyy-MM-dd HH:mm:ss.SSS", "yyyy-MM-dd HH:mm:ss", "MM-dd HH:mm", "hh:mm:ss.SSS a", "hh:mm:ss a", "EEEE HH:mm:ss.SSS"};
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, presets));
        root.addView(spinner, matchWrap());
        formatEdit = addLabeledEdit(root, "自定义时间格式", ClockPrefs.getFormat(prefs), InputType.TYPE_CLASS_TEXT);
        spinner.setSelection(0, false);

        sizeSeek.setOnSeekBarChangeListener(simpleSeekUpdater());
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) {
                    formatEdit.setText(presets[position]);
                    updatePreview();
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        View.OnFocusChangeListener previewUpdater = (v, hasFocus) -> { if (!hasFocus) updatePreview(); };
        textColorEdit.setOnFocusChangeListener(previewUpdater);
        shadowColorEdit.setOnFocusChangeListener(previewUpdater);
        formatEdit.setOnFocusChangeListener(previewUpdater);
        boldCheck.setOnCheckedChangeListener((buttonView, isChecked) -> updatePreview());
        pickTextColorButton.setOnClickListener(v -> ColorPickerDialog.show(this, "选择悬浮文字颜色", textColorEdit.getText().toString(), false, color -> { textColorEdit.setText(color); updatePreview(); }));
        pickShadowColorButton.setOnClickListener(v -> ColorPickerDialog.show(this, "选择悬浮阴影颜色", shadowColorEdit.getText().toString(), true, color -> { shadowColorEdit.setText(color); updatePreview(); }));
    }

    private void buildDesktopSection(LinearLayout root) {
        TextView tip = sectionTip("桌面时钟支持组件独立样式、可视化拖动布局和预设；普通模式长按屏幕退出。");
        root.addView(tip, matchWrap());
        buildPresetControls(root);
        desktopTextColorEdit = addLabeledEdit(root, "桌面文字颜色", ClockPrefs.getDesktopTextColor(prefs), InputType.TYPE_CLASS_TEXT);
        Button pickTextColorButton = secondaryButton("选择桌面文字颜色");
        root.addView(pickTextColorButton, matchWrap());

        desktopShadowColorEdit = addLabeledEdit(root, "桌面阴影颜色", ClockPrefs.getDesktopShadowColor(prefs), InputType.TYPE_CLASS_TEXT);
        Button pickShadowColorButton = secondaryButton("选择桌面阴影颜色");
        root.addView(pickShadowColorButton, matchWrap());

        desktopSizeLabel = label("桌面字体大小");
        root.addView(desktopSizeLabel, matchWrap());
        desktopSizeSeek = new SeekBar(this);
        desktopSizeSeek.setMax(180 - 36);
        desktopSizeSeek.setProgress(ClockPrefs.getDesktopTextSize(prefs) - 36);
        root.addView(desktopSizeSeek, matchWrap());

        desktopBoldCheck = new CheckBox(this);
        desktopBoldCheck.setText("桌面粗体显示");
        desktopBoldCheck.setTextColor(Color.rgb(24, 32, 56));
        desktopBoldCheck.setChecked(ClockPrefs.isDesktopBold(prefs));
        root.addView(desktopBoldCheck, matchWrap());

        Button editLayoutButton = primaryButton("可视化编辑组件位置");
        root.addView(editLayoutButton, matchWrap());
        editLayoutButton.setOnClickListener(v -> {
            if (saveAllSettings(false)) {
                startActivity(new Intent(this, DesktopClockActivity.class)
                        .putExtra(DesktopClockActivity.EXTRA_EDIT_MODE, true));
            }
        });

        root.addView(label("日期组件"), matchWrap());
        desktopDateCheck = styledCheck("显示日期", prefs.getBoolean(DesktopConfig.KEY_SHOW_DATE, true));
        root.addView(desktopDateCheck, matchWrap());
        dateColorEdit = addLabeledEdit(root, "日期颜色", prefs.getString(DesktopConfig.KEY_DATE_COLOR, "#D2FFFFFF"), InputType.TYPE_CLASS_TEXT);
        dateSizeSeek = addSizeSeek(root, "日期字号", prefs.getInt(DesktopConfig.KEY_DATE_SIZE, 24), 12, 48);
        dateBoldCheck = styledCheck("日期粗体", prefs.getBoolean(DesktopConfig.KEY_DATE_BOLD, true));
        root.addView(dateBoldCheck, matchWrap());

        root.addView(label("电量组件"), matchWrap());
        desktopBatteryCheck = new CheckBox(this);
        desktopBatteryCheck.setText("显示电量");
        desktopBatteryCheck.setTextColor(Color.rgb(24, 32, 56));
        desktopBatteryCheck.setChecked(ClockPrefs.showDesktopBattery(prefs));
        root.addView(desktopBatteryCheck, matchWrap());
        batteryColorEdit = addLabeledEdit(root, "电量颜色", prefs.getString(DesktopConfig.KEY_BATTERY_COLOR, "#B9FFFFFF"), InputType.TYPE_CLASS_TEXT);
        batterySizeSeek = addSizeSeek(root, "电量字号", prefs.getInt(DesktopConfig.KEY_BATTERY_SIZE, 20), 12, 48);
        batteryBoldCheck = styledCheck("电量粗体", prefs.getBoolean(DesktopConfig.KEY_BATTERY_BOLD, true));
        root.addView(batteryBoldCheck, matchWrap());

        root.addView(label("网速组件"), matchWrap());
        desktopNetworkCheck = new CheckBox(this);
        desktopNetworkCheck.setText("显示上下行网速");
        desktopNetworkCheck.setTextColor(Color.rgb(24, 32, 56));
        desktopNetworkCheck.setChecked(ClockPrefs.showDesktopNetwork(prefs));
        root.addView(desktopNetworkCheck, matchWrap());
        networkColorEdit = addLabeledEdit(root, "网速颜色", prefs.getString(DesktopConfig.KEY_NETWORK_COLOR, "#B9FFFFFF"), InputType.TYPE_CLASS_TEXT);
        networkSizeSeek = addSizeSeek(root, "网速字号", prefs.getInt(DesktopConfig.KEY_NETWORK_SIZE, 18), 12, 48);
        networkBoldCheck = styledCheck("网速粗体", prefs.getBoolean(DesktopConfig.KEY_NETWORK_BOLD, true));
        root.addView(networkBoldCheck, matchWrap());

        root.addView(label("CPU 面板"), matchWrap());
        desktopCpuCheck = new CheckBox(this);
        desktopCpuCheck.setText("显示 CPU 监控");
        desktopCpuCheck.setTextColor(Color.rgb(24, 32, 56));
        desktopCpuCheck.setChecked(ClockPrefs.showDesktopCpu(prefs));
        root.addView(desktopCpuCheck, matchWrap());
        cpuWidthSeek = addSizeSeek(root, "CPU 面板宽度", prefs.getInt(DesktopConfig.KEY_CPU_WIDTH, 140), 100, 320);
        cpuHeightSeek = addSizeSeek(root, "CPU 面板高度", prefs.getInt(DesktopConfig.KEY_CPU_HEIGHT, 258), 160, 420);
        cpuAlphaSeek = addSizeSeek(root, "CPU 背景透明度", prefs.getInt(DesktopConfig.KEY_CPU_ALPHA, 100), 10, 100);

        root.addView(label("天气组件 (Open-Meteo 免Key)"), matchWrap());
        desktopWeatherCheck = styledCheck("显示天气组件", prefs.getBoolean(DesktopConfig.KEY_SHOW_WEATHER, true));
        root.addView(desktopWeatherCheck, matchWrap());
        weatherCityEdit = addLabeledEdit(root, "天气城市", prefs.getString(DesktopConfig.KEY_WEATHER_CITY, "北京"), InputType.TYPE_CLASS_TEXT);
        Button queryCityButton = secondaryButton("查询城市经纬度");
        root.addView(queryCityButton, matchWrap());
        queryCityButton.setOnClickListener(v -> {
            String city = weatherCityEdit.getText().toString().trim();
            if (city.isEmpty()) {
                toast("请输入城市名称，例如：北京、深圳、上海");
                return;
            }
            toast("正在查询 " + city + " 经纬度...");
            WeatherManager.searchCity(city, new WeatherManager.CitySearchCallback() {
                @Override
                public void onSuccess(String cityName, float lat, float lon) {
                    prefs.edit()
                            .putString(DesktopConfig.KEY_WEATHER_CITY, cityName)
                            .putFloat(DesktopConfig.KEY_WEATHER_LAT, lat)
                            .putFloat(DesktopConfig.KEY_WEATHER_LON, lon)
                            .apply();
                    weatherCityEdit.setText(cityName);
                    toast(String.format(java.util.Locale.getDefault(), "已绑定: %s (%.2f, %.2f)", cityName, lat, lon));
                    WeatherManager.fetchWeather(MainActivity.this, lat, lon, true, new WeatherManager.WeatherCallback() {
                        @Override public void onSuccess(String json) { toast("天气数据已刷新"); updatePreview(); }
                        @Override public void onError(String msg) { }
                    });
                }
                @Override
                public void onError(String message) { toast(message); }
            });
        });

        Button refreshWeatherButton = secondaryButton("立即刷新天气缓存");
        root.addView(refreshWeatherButton, matchWrap());
        refreshWeatherButton.setOnClickListener(v -> {
            float lat = prefs.getFloat(DesktopConfig.KEY_WEATHER_LAT, 39.9042f);
            float lon = prefs.getFloat(DesktopConfig.KEY_WEATHER_LON, 116.4074f);
            toast("正在获取最新天气...");
            WeatherManager.fetchWeather(this, lat, lon, true, new WeatherManager.WeatherCallback() {
                @Override
                public void onSuccess(String json) {
                    toast("天气已更新");
                    updatePreview();
                }
                @Override
                public void onError(String message) {
                    toast("更新失败: " + message);
                }
            });
        });

        root.addView(label("默认展示卡片 (桌面点击卡片可直接切换)"), matchWrap());
        weatherModeSpinner = new Spinner(this);
        String[] cardModeNames = new String[]{"三日对比卡片 (昨/今/明)", "逐小时走势卡片 (前/现/未来)"};
        weatherModeSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, cardModeNames));
        weatherModeSpinner.setSelection(Math.max(0, Math.min(cardModeNames.length - 1, prefs.getInt(DesktopConfig.KEY_WEATHER_MODE, 0))));
        root.addView(weatherModeSpinner, matchWrap());

        weatherWidthSeek = addSizeSeek(root, "天气卡片宽度", prefs.getInt(DesktopConfig.KEY_WEATHER_WIDTH, 360), 240, 520);
        weatherHeightSeek = addSizeSeek(root, "天气卡片高度", prefs.getInt(DesktopConfig.KEY_WEATHER_HEIGHT, 168), 120, 260);
        weatherAlphaSeek = addSizeSeek(root, "天气卡片透明度", prefs.getInt(DesktopConfig.KEY_WEATHER_ALPHA, 100), 10, 100);

        weatherColorEdit = addLabeledEdit(root, "天气文字颜色", prefs.getString(DesktopConfig.KEY_WEATHER_COLOR, "#D2FFFFFF"), InputType.TYPE_CLASS_TEXT);
        Button pickWeatherColorButton = secondaryButton("选择天气文字颜色");
        root.addView(pickWeatherColorButton, matchWrap());
        pickWeatherColorButton.setOnClickListener(v -> ColorPickerDialog.show(this, "选择天气文字颜色", weatherColorEdit.getText().toString(), false, color -> { weatherColorEdit.setText(color); updatePreview(); }));
        weatherBoldCheck = styledCheck("天气粗体", prefs.getBoolean(DesktopConfig.KEY_WEATHER_BOLD, true));
        root.addView(weatherBoldCheck, matchWrap());

        cpuOverlayCheck = new CheckBox(this);
        cpuOverlayCheck.setText("悬浮窗显示 CPU 占用（可拖动）");
        cpuOverlayCheck.setTextColor(Color.rgb(24, 32, 56));
        cpuOverlayCheck.setChecked(ClockPrefs.showCpuOverlay(prefs));
        root.addView(cpuOverlayCheck, matchWrap());

        TextView presetLabel = label("桌面时间格式（无毫秒）");
        root.addView(presetLabel, matchWrap());
        Spinner spinner = new Spinner(this);
        String[] presets = new String[]{"不使用预设", "HH:mm:ss", "HH:mm", "yyyy-MM-dd HH:mm:ss", "MM-dd HH:mm", "hh:mm:ss a", "EEEE HH:mm:ss"};
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, presets));
        root.addView(spinner, matchWrap());
        desktopFormatEdit = addLabeledEdit(root, "桌面自定义时间格式", ClockPrefs.getDesktopFormat(prefs), InputType.TYPE_CLASS_TEXT);
        spinner.setSelection(0, false);

        desktopSizeSeek.setOnSeekBarChangeListener(simpleSeekUpdater());
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) {
                    desktopFormatEdit.setText(presets[position]);
                    updatePreview();
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        View.OnFocusChangeListener previewUpdater = (v, hasFocus) -> { if (!hasFocus) updatePreview(); };
        desktopTextColorEdit.setOnFocusChangeListener(previewUpdater);
        desktopShadowColorEdit.setOnFocusChangeListener(previewUpdater);
        desktopFormatEdit.setOnFocusChangeListener(previewUpdater);
        desktopBoldCheck.setOnCheckedChangeListener((buttonView, isChecked) -> updatePreview());
        desktopDateCheck.setOnCheckedChangeListener((buttonView, isChecked) -> updatePreview());
        desktopBatteryCheck.setOnCheckedChangeListener((buttonView, isChecked) -> updatePreview());
        desktopNetworkCheck.setOnCheckedChangeListener((buttonView, isChecked) -> updatePreview());
        desktopCpuCheck.setOnCheckedChangeListener((buttonView, isChecked) -> updatePreview());
        desktopWeatherCheck.setOnCheckedChangeListener((buttonView, isChecked) -> updatePreview());
        cpuOverlayCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(ClockPrefs.KEY_CPU_OVERLAY, isChecked).commit();
            syncCpuOverlay(true);
        });
        pickTextColorButton.setOnClickListener(v -> ColorPickerDialog.show(this, "选择桌面文字颜色", desktopTextColorEdit.getText().toString(), false, color -> { desktopTextColorEdit.setText(color); updatePreview(); }));
        pickShadowColorButton.setOnClickListener(v -> ColorPickerDialog.show(this, "选择桌面阴影颜色", desktopShadowColorEdit.getText().toString(), true, color -> { desktopShadowColorEdit.setText(color); updatePreview(); }));
    }

    private CheckBox styledCheck(String text, boolean checked) {
        CheckBox check = new CheckBox(this);
        check.setText(text);
        check.setTextColor(Color.rgb(24, 32, 56));
        check.setChecked(checked);
        return check;
    }

    private SeekBar addSizeSeek(LinearLayout root, String title, int value, int min, int max) {
        TextView valueLabel = label(title + "：" + value);
        root.addView(valueLabel, matchWrap());
        SeekBar seek = new SeekBar(this);
        seek.setMax(max - min);
        seek.setProgress(Math.max(0, Math.min(max - min, value - min)));
        seek.setTag(new int[]{min, max});
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                valueLabel.setText(title + "：" + (min + progress));
                updatePreview();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        root.addView(seek, matchWrap());
        return seek;
    }

    private int seekValue(SeekBar seek, int defaultValue) {
        if (seek == null || !(seek.getTag() instanceof int[])) return defaultValue;
        return ((int[]) seek.getTag())[0] + seek.getProgress();
    }

    private SeekBar.OnSeekBarChangeListener simpleSeekUpdater() {        return new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { updatePreview(); }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        };
    }

    private void buildPresetControls(LinearLayout root) {
        root.addView(label("布局预设"), matchWrap());
        desktopPresetSpinner = new Spinner(this);
        desktopPresetAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                DesktopConfig.presetNames(prefs));
        desktopPresetSpinner.setAdapter(desktopPresetAdapter);
        root.addView(desktopPresetSpinner, matchWrap());

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        Button save = secondaryButton("保存预设");
        Button apply = secondaryButton("应用预设");
        Button rename = secondaryButton("重命名");
        Button delete = secondaryButton("删除");
        row1.addView(save, weightWrap(1)); row1.addView(apply, weightWrap(1));
        row1.addView(rename, weightWrap(1)); row1.addView(delete, weightWrap(1));
        root.addView(row1, matchWrap());

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        Button export = secondaryButton("复制 JSON");
        Button importButton = secondaryButton("导入 JSON");
        row2.addView(export, weightWrap(1)); row2.addView(importButton, weightWrap(1));
        root.addView(row2, matchWrap());

        save.setOnClickListener(v -> promptText("保存预设", "预设名称", name -> {
            if (!saveAllSettings(false)) return;
            try { DesktopConfig.savePreset(prefs, name); refreshPresetSpinner(); toast("预设已保存"); }
            catch (Exception e) { toast("保存失败：" + e.getMessage()); }
        }));
        apply.setOnClickListener(v -> {
            int position = desktopPresetSpinner.getSelectedItemPosition();
            if (position < 0) return;
            try { DesktopConfig.applyPreset(prefs, position); recreate(); }
            catch (Exception e) { toast("应用失败：" + e.getMessage()); }
        });
        rename.setOnClickListener(v -> {
            int position = desktopPresetSpinner.getSelectedItemPosition();
            if (position < 0) return;
            promptText("重命名预设", "新名称", name -> {
                try { DesktopConfig.renamePreset(prefs, position, name); refreshPresetSpinner(); }
                catch (Exception e) { toast("重命名失败"); }
            });
        });
        delete.setOnClickListener(v -> {
            int position = desktopPresetSpinner.getSelectedItemPosition();
            if (position < 0) return;
            try { DesktopConfig.deletePreset(prefs, position); refreshPresetSpinner(); }
            catch (Exception e) { toast("删除失败"); }
        });
        export.setOnClickListener(v -> {
            try {
                String json = DesktopConfig.exportAll(prefs);
                ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("桌面时钟预设", json));
                toast("JSON 已复制到剪贴板");
            } catch (Exception e) { toast("导出失败"); }
        });
        importButton.setOnClickListener(v -> promptText("导入 JSON", "粘贴导出的 JSON", json -> {
            try { DesktopConfig.importAll(prefs, json); toast("导入成功"); recreate(); }
            catch (Exception e) { Toast.makeText(this, "导入失败：JSON 无效", Toast.LENGTH_LONG).show(); }
        }, true));
    }

    private interface TextCallback { void accept(String value); }

    private void promptText(String title, String hint, TextCallback callback) { promptText(title, hint, callback, false); }

    private void promptText(String title, String hint, TextCallback callback, boolean multiline) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setSingleLine(!multiline);
        if (multiline) input.setMinLines(8);
        new AlertDialog.Builder(this).setTitle(title).setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", (d, w) -> {
                    String value = input.getText().toString().trim();
                    if (!value.isEmpty()) callback.accept(value); else toast("内容不能为空");
                }).show();
    }

    private void refreshPresetSpinner() {
        desktopPresetAdapter.clear();
        desktopPresetAdapter.addAll(DesktopConfig.presetNames(prefs));
        desktopPresetAdapter.notifyDataSetChanged();
    }

    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show(); }

    private void showTab(boolean desktop) {
        showingDesktopTab = desktop;
        floatingSection.setVisibility(desktop ? View.GONE : View.VISIBLE);
        desktopSection.setVisibility(desktop ? View.VISIBLE : View.GONE);
        floatingTab.setEnabled(desktop);
        desktopTab.setEnabled(!desktop);
        floatingTab.setText(desktop ? "悬浮时钟" : "✓ 悬浮时钟");
        desktopTab.setText(desktop ? "✓ 桌面时钟" : "桌面时钟");
        if (scrollView != null) scrollView.post(() -> scrollView.smoothScrollTo(0, 0));
        updatePreview();
    }

    private EditText addLabeledEdit(LinearLayout root, String label, String value, int inputType) {
        root.addView(label(label), matchWrap());
        EditText edit = new EditText(this);
        edit.setSingleLine(true);
        edit.setInputType(inputType);
        edit.setText(value);
        edit.setTextColor(Color.rgb(24, 32, 56));
        edit.setHintTextColor(Color.rgb(120, 132, 160));
        edit.setBackgroundColor(Color.rgb(245, 247, 252));
        edit.setPadding(dp(10), 0, dp(10), 0);
        root.addView(edit, matchWrap());
        return edit;
    }

    private boolean saveAllSettings(boolean showToast) {
        String textColor = textColorEdit.getText().toString().trim();
        String shadowColor = shadowColorEdit.getText().toString().trim();
        String format = formatEdit.getText().toString().trim();
        String desktopTextColor = desktopTextColorEdit.getText().toString().trim();
        String desktopShadowColor = desktopShadowColorEdit.getText().toString().trim();
        String desktopFormat = ClockPrefs.stripMilliseconds(desktopFormatEdit.getText().toString().trim());
        String dateColor = dateColorEdit.getText().toString().trim();
        String batteryColor = batteryColorEdit.getText().toString().trim();
        String networkColor = networkColorEdit.getText().toString().trim();
        String weatherCity = weatherCityEdit != null ? weatherCityEdit.getText().toString().trim() : "北京";
        String weatherColor = weatherColorEdit != null ? weatherColorEdit.getText().toString().trim() : "#D2FFFFFF";
        if (weatherCity.isEmpty()) weatherCity = "北京";
        if (format.isEmpty()) format = ClockPrefs.DEFAULT_FORMAT;
        if (desktopFormat.isEmpty()) desktopFormat = ClockPrefs.DEFAULT_DESKTOP_FORMAT;
        try {
            Color.parseColor(textColor);
            Color.parseColor(shadowColor);
            Color.parseColor(desktopTextColor);
            Color.parseColor(desktopShadowColor);
            Color.parseColor(dateColor);
            Color.parseColor(batteryColor);
            Color.parseColor(networkColor);
            Color.parseColor(weatherColor);
            ClockPrefs.validateFormat(format);
            ClockPrefs.validateFormat(desktopFormat);
        } catch (IllegalArgumentException e) {
            Toast.makeText(this, "设置无效：" + e.getMessage(), Toast.LENGTH_LONG).show();
            return false;
        }
        desktopFormatEdit.setText(desktopFormat);
        prefs.edit()
                .putString(ClockPrefs.KEY_TEXT_COLOR, textColor)
                .putString(ClockPrefs.KEY_SHADOW_COLOR, shadowColor)
                .putInt(ClockPrefs.KEY_TEXT_SIZE, currentFloatingSize())
                .putString(ClockPrefs.KEY_FORMAT, format)
                .putBoolean(ClockPrefs.KEY_BOLD, boldCheck.isChecked())
                .putString(ClockPrefs.KEY_DESKTOP_TEXT_COLOR, desktopTextColor)
                .putString(ClockPrefs.KEY_DESKTOP_SHADOW_COLOR, desktopShadowColor)
                .putInt(ClockPrefs.KEY_DESKTOP_TEXT_SIZE, currentDesktopSize())
                .putString(ClockPrefs.KEY_DESKTOP_FORMAT, desktopFormat)
                .putBoolean(ClockPrefs.KEY_DESKTOP_BOLD, desktopBoldCheck.isChecked())
                .putBoolean(DesktopConfig.KEY_SHOW_DATE, desktopDateCheck.isChecked())
                .putString(DesktopConfig.KEY_DATE_COLOR, dateColor)
                .putInt(DesktopConfig.KEY_DATE_SIZE, seekValue(dateSizeSeek, 24))
                .putBoolean(DesktopConfig.KEY_DATE_BOLD, dateBoldCheck.isChecked())
                .putBoolean(ClockPrefs.KEY_DESKTOP_SHOW_BATTERY, desktopBatteryCheck.isChecked())
                .putString(DesktopConfig.KEY_BATTERY_COLOR, batteryColor)
                .putInt(DesktopConfig.KEY_BATTERY_SIZE, seekValue(batterySizeSeek, 20))
                .putBoolean(DesktopConfig.KEY_BATTERY_BOLD, batteryBoldCheck.isChecked())
                .putBoolean(ClockPrefs.KEY_DESKTOP_SHOW_NETWORK, desktopNetworkCheck.isChecked())
                .putString(DesktopConfig.KEY_NETWORK_COLOR, networkColor)
                .putInt(DesktopConfig.KEY_NETWORK_SIZE, seekValue(networkSizeSeek, 18))
                .putBoolean(DesktopConfig.KEY_NETWORK_BOLD, networkBoldCheck.isChecked())
                .putBoolean(ClockPrefs.KEY_DESKTOP_SHOW_CPU, desktopCpuCheck.isChecked())
                .putInt(DesktopConfig.KEY_CPU_WIDTH, seekValue(cpuWidthSeek, 140))
                .putInt(DesktopConfig.KEY_CPU_HEIGHT, seekValue(cpuHeightSeek, 258))
                .putInt(DesktopConfig.KEY_CPU_ALPHA, seekValue(cpuAlphaSeek, 100))
                .putBoolean(DesktopConfig.KEY_SHOW_WEATHER, desktopWeatherCheck != null && desktopWeatherCheck.isChecked())
                .putString(DesktopConfig.KEY_WEATHER_CITY, weatherCity)
                .putInt(DesktopConfig.KEY_WEATHER_MODE, weatherModeSpinner != null ? weatherModeSpinner.getSelectedItemPosition() : 0)
                .putString(DesktopConfig.KEY_WEATHER_COLOR, weatherColor)
                .putBoolean(DesktopConfig.KEY_WEATHER_BOLD, weatherBoldCheck != null && weatherBoldCheck.isChecked())
                .putInt(DesktopConfig.KEY_WEATHER_WIDTH, seekValue(weatherWidthSeek, 360))
                .putInt(DesktopConfig.KEY_WEATHER_HEIGHT, seekValue(weatherHeightSeek, 168))
                .putInt(DesktopConfig.KEY_WEATHER_ALPHA, seekValue(weatherAlphaSeek, 100))
                .putBoolean(ClockPrefs.KEY_CPU_OVERLAY, cpuOverlayCheck.isChecked())
                .commit();
        updatePreview();
        syncCpuOverlay(false);
        sendBroadcast(new Intent(ClockOverlayService.ACTION_PREFS_CHANGED));
        if (showToast) Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
        return true;
    }

    /** 按开关状态启动 / 停止悬浮 CPU 监控。 */
    private void syncCpuOverlay(boolean askPermission) {
        if (cpuOverlayCheck == null) return;
        boolean want = cpuOverlayCheck.isChecked();
        if (!want) {
            stopService(new Intent(this, CpuOverlayService.class));
            return;
        }
        if (!canDrawOverlays()) {
            if (askPermission) {
                Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_LONG).show();
                requestOverlayPermission();
            }
            return;
        }
        startService(new Intent(this, CpuOverlayService.class));
    }

    private void startClock() {
        if (!canDrawOverlays()) {
            Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_LONG).show();
            requestOverlayPermission();
            return;
        }
        startService(new Intent(this, ClockOverlayService.class));
    }

    private void requestOverlayPermission() {
        if (canDrawOverlays()) {
            Toast.makeText(this, "已拥有悬浮窗权限", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
        startActivityForResult(intent, REQ_OVERLAY);
    }

    private boolean canDrawOverlays() { return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this); }

    private void updatePreview() {
        if (preview == null) return;
        if (showingDesktopTab) updateDesktopPreview(); else updateFloatingPreview();
        updateTabStyles();
    }

    private void updateFloatingPreview() {
        if (sizeLabel != null) sizeLabel.setText("字体大小：" + currentFloatingSize() + "sp");
        preview.setText("12:34:56.789");
        preview.setTextSize(Math.min(44, currentFloatingSize()));
        preview.setTypeface(Typeface.DEFAULT, boldCheck != null && boldCheck.isChecked() ? Typeface.BOLD : Typeface.NORMAL);
        applyPreviewColors(textColorEdit, shadowColorEdit);
    }

    private void updateDesktopPreview() {
        if (desktopSizeLabel != null) desktopSizeLabel.setText("桌面字体大小：" + currentDesktopSize() + "sp");
        StringBuilder previewText = new StringBuilder("12:34:56");
        if (desktopDateCheck != null && desktopDateCheck.isChecked()) previewText.append("\n2026-01-01 星期四");
        if (desktopBatteryCheck != null && desktopBatteryCheck.isChecked()) previewText.append("  ·  电量 88%");
        if (desktopNetworkCheck != null && desktopNetworkCheck.isChecked()) previewText.append("\n↓ 1.2 MB/s\n↑ 128 KB/s");
        if (desktopWeatherCheck != null && desktopWeatherCheck.isChecked()) {
            String city = weatherCityEdit != null ? weatherCityEdit.getText().toString().trim() : "北京";
            if (city.isEmpty()) city = "北京";
            int mode = weatherModeSpinner != null ? weatherModeSpinner.getSelectedItemPosition() : 0;
            if (mode == 0) {
                previewText.append("\n📍 ").append(city).append(" · 三日卡片:\n[昨 23~11° 晴] [今 21~11° 多云] [明 22~11° 小雨]");
            } else {
                previewText.append("\n📍 ").append(city).append(" · 逐小时卡片:\n[12:00 19°] [13:00 20° 现] [14:00 21°] [15:00 21°]");
            }
        }
        preview.setText(previewText.toString());
        preview.setTextSize(Math.min(72, currentDesktopSize()));
        preview.setTypeface(Typeface.DEFAULT, desktopBoldCheck != null && desktopBoldCheck.isChecked() ? Typeface.BOLD : Typeface.NORMAL);
        applyPreviewColors(desktopTextColorEdit, desktopShadowColorEdit);
    }

    private void applyPreviewColors(EditText textEdit, EditText shadowEdit) {
        int textColor;
        try { textColor = Color.parseColor(textEdit.getText().toString().trim()); } catch (Exception ignored) { textColor = Color.WHITE; }
        preview.setTextColor(ensureContrastOnDark(textColor));
        int shadow;
        try { shadow = Color.parseColor(shadowEdit.getText().toString().trim()); } catch (Exception ignored) { shadow = Color.BLACK; }
        preview.setShadowLayer(6f, 2f, 2f, shadow);
        preview.setBackground(cardBg(Color.rgb(20, 24, 38), 20));
    }

    private int ensureContrastOnDark(int color) {
        int alpha = Color.alpha(color);
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        double luminance = 0.299 * r + 0.587 * g + 0.114 * b;
        if (alpha < 150 || luminance < 130) {
            return Color.rgb(
                    Math.max(r, 225),
                    Math.max(g, 225),
                    Math.max(b, 225));
        }
        return color;
    }

    private int currentFloatingSize() { return 12 + (sizeSeek == null ? (ClockPrefs.getTextSize(prefs) - 12) : sizeSeek.getProgress()); }
    private int currentDesktopSize() { return 36 + (desktopSizeSeek == null ? (ClockPrefs.getDesktopTextSize(prefs) - 36) : desktopSizeSeek.getProgress()); }

    private void updateTabStyles() {
        if (floatingTab == null || desktopTab == null) return;
        styleButton(floatingTab, !showingDesktopTab, false);
        styleButton(desktopTab, showingDesktopTab, false);
    }

    private Button primaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        styleButton(button, true, true);
        return button;
    }

    private Button secondaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        styleButton(button, false, true);
        return button;
    }

    private void styleButton(Button button, boolean primary, boolean compactText) {
        button.setAllCaps(false);
        button.setTextSize(compactText ? 13 : 14);
        button.setSingleLine(true);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setTypeface(Typeface.DEFAULT, primary ? Typeface.BOLD : Typeface.NORMAL);
        button.setTextColor(primary ? Color.WHITE : Color.rgb(67, 83, 130));
        button.setBackground(cardBg(primary ? Color.rgb(84, 112, 255) : Color.rgb(235, 239, 255), 16));
        button.setMinHeight(dp(42));
        button.setPadding(dp(8), 0, dp(8), 0);
    }

    private GradientDrawable cardBg(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private TextView label(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(15);
        tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        tv.setTextColor(Color.rgb(55, 67, 105));
        tv.setPadding(0, dp(12), 0, dp(3));
        return tv;
    }

    private TextView sectionTip(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setTextColor(Color.rgb(105, 116, 150));
        tv.setPadding(0, dp(6), 0, dp(4));
        return tv;
    }

    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); }

    private LinearLayout.LayoutParams weightWrap(float weight) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        int margin = dp(3);
        params.setMargins(margin, 0, margin, 0);
        return params;
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
}
