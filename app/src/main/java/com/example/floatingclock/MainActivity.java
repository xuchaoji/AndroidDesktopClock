package com.example.floatingclock;

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
    private CheckBox desktopCpuCheck;
    private CheckBox cpuOverlayCheck;

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
        TextView tip = sectionTip("桌面时钟黑底全屏常亮；不支持毫秒；文字会定期轻微移动，降低烧屏风险。");
        root.addView(tip, matchWrap());
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

        desktopBatteryCheck = new CheckBox(this);
        desktopBatteryCheck.setText("显示电量");
        desktopBatteryCheck.setTextColor(Color.rgb(24, 32, 56));
        desktopBatteryCheck.setChecked(ClockPrefs.showDesktopBattery(prefs));
        root.addView(desktopBatteryCheck, matchWrap());

        desktopCpuCheck = new CheckBox(this);
        desktopCpuCheck.setText("显示 CPU 监控");
        desktopCpuCheck.setTextColor(Color.rgb(24, 32, 56));
        desktopCpuCheck.setChecked(ClockPrefs.showDesktopCpu(prefs));
        root.addView(desktopCpuCheck, matchWrap());

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
        desktopBatteryCheck.setOnCheckedChangeListener((buttonView, isChecked) -> updatePreview());
        desktopCpuCheck.setOnCheckedChangeListener((buttonView, isChecked) -> updatePreview());
        cpuOverlayCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(ClockPrefs.KEY_CPU_OVERLAY, isChecked).commit();
            syncCpuOverlay(true);
        });
        pickTextColorButton.setOnClickListener(v -> ColorPickerDialog.show(this, "选择桌面文字颜色", desktopTextColorEdit.getText().toString(), false, color -> { desktopTextColorEdit.setText(color); updatePreview(); }));
        pickShadowColorButton.setOnClickListener(v -> ColorPickerDialog.show(this, "选择桌面阴影颜色", desktopShadowColorEdit.getText().toString(), true, color -> { desktopShadowColorEdit.setText(color); updatePreview(); }));
    }

    private SeekBar.OnSeekBarChangeListener simpleSeekUpdater() {        return new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { updatePreview(); }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        };
    }

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
        if (format.isEmpty()) format = ClockPrefs.DEFAULT_FORMAT;
        if (desktopFormat.isEmpty()) desktopFormat = ClockPrefs.DEFAULT_DESKTOP_FORMAT;
        try {
            Color.parseColor(textColor);
            Color.parseColor(shadowColor);
            Color.parseColor(desktopTextColor);
            Color.parseColor(desktopShadowColor);
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
                .putBoolean(ClockPrefs.KEY_DESKTOP_SHOW_BATTERY, desktopBatteryCheck.isChecked())
                .putBoolean(ClockPrefs.KEY_DESKTOP_SHOW_CPU, desktopCpuCheck.isChecked())
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
        preview.setText(desktopBatteryCheck != null && desktopBatteryCheck.isChecked() ? "12:34:56  ·  电量 88%" : "12:34:56");
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
