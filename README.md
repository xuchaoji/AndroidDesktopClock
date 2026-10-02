# AndroidDesktopClock

Android 桌面时钟 / 悬浮时钟应用。

一个 App 内含两个时钟形态，样式和格式各自独立可配：

| 形态 | 说明 |
|---|---|
| **悬浮时钟** | 悬浮在其它应用之上，可拖动；支持毫秒刷新 |
| **桌面时钟** | 黑底全屏沉浸式摆钟页面，常亮显示，可作床头/桌面钟 |

## 功能

### 通用样式设置
- 文字颜色、阴影颜色：内置**选色盘**（色相/饱和度二维选择 + 亮度滑条，阴影支持透明度滑条），也支持直接输入 `#RRGGBB` / `#AARRGGBB`
- 字体大小：悬浮 12–72sp，桌面 36–180sp
- 粗体开关
- 时间格式：使用 Java `SimpleDateFormat` 语法，提供常用预设，也支持自定义

### 悬浮时钟
- 时间格式支持毫秒（格式含 `S` 时自动切到毫秒级刷新）
- 可拖动，位置自动记忆
- 设置保存后实时刷新，无需重启

### 桌面时钟
- 黑底全屏、沉浸式、保持屏幕常亮
- 组件化定制：主时钟、日期、电量、网速、**天气组件**可分别配置颜色、字号、粗体和显示状态
- **精美结构化天气卡片（WeatherCardView，基于 Open-Meteo 免 Key API）**：
  - 采用结构化表格卡片呈现（含顶部实时概况摘要、左侧表头行、高亮当前列、分割线与毛玻璃透明外框）
  - 点击卡片直接在两大卡片形态之间**一键切换**：
    1. **三日对比卡片**：
       - `日期`：`10/1(昨)`、`10/2(今 高亮)`、`10/3(明)`
       - `天气`：`☀️ 晴`、`⛅ 多云`、`🌧️ 小雨`
       - `温度`：上下立体排布 `高温° / ~ / 低温°`（橙蓝冷暖配色）
    2. **逐小时走势卡片**：
       - `时间`：`12:00(一小时前)`、`13:00(现在 高亮)`、`14:00(+1h)`、`15:00(+2h)`、`16:00(+3h)`
       - `天气`：对应各小时的天气图标与天气状况
       - `温度`：对应各小时的实时/预测温度
  - 长按天气卡片立即强制刷新网络数据
  - 支持城市名称搜索与经纬度自动绑定，内置 20 分钟缓存与离线回退机制
  - 支持在设置页独立配置卡片宽度、高度、背景透明度与文字颜色
- 可视化布局编辑：主时钟、日期、电量、网速、CPU 面板、天气卡片均可拖动，位置按屏幕比例保存
- 布局预设：支持保存、应用、重命名、删除，以及通过剪贴板导入/导出 JSON
- 右下角以固定宽度双行显示实时上下行网速（可关闭）
- 左侧 **CPU 占用监控**（可关闭）：
  - 读取 `/proc/stat` 计算每核心占用率
  - 读取 `/sys/.../cpufreq/cpuinfo_max_freq` 识别大小核
  - 大核全宽正常大小，小核半宽、两个一行紧凑排布
  - 每核心显示「频率档位 + 占用率」和最近约 60 秒曲线
  - 面板标题显示 CPU 温度；多传感器设备取 CPU 相关热区中的最高温度
  - 读取失败时回退到 `scaling_cur_freq / cpuinfo_max_freq` 估算
- **防烧屏**：时钟、提示文字、信息面板、CPU 面板都会定期做缓慢的上下漂移
- 左侧区域上下滑动可调亮度；点击屏幕退出

### 悬浮窗 CPU 监控
- 独立开关，把同一套 CPU 渲染面板悬浮到其它应用之上
- 可拖动，位置自动记忆
- 开关状态持久化，重启后自动恢复

## 构建

标准 Android Gradle 工程（Java，非 Kotlin），JDK 17 + Gradle 8.9 + Android SDK 35。

```bash
# 依赖：JDK 17、Android SDK platform 35、build-tools 35.0.0
./gradlew assembleDebug

# 产物
app/build/outputs/apk/debug/app-debug.apk
```

`app/build.gradle` 中显式声明了 `buildToolsVersion "35.0.0"`，避免 AGP 去下载它自己的默认版本。
`local.properties`（含本机 SDK 路径）已被 `.gitignore` 忽略，需要自行创建：

```properties
sdk.dir=/path/to/android-sdk
```

## 发布（GitHub Actions 自动出包）

推一个 `v*` 标签即可自动构建 APK 并挂到 Release：

```bash
git tag v1.0
git push origin v1.0
```

随后可在 `https://github.com/xuchaoji/AndroidDesktopClock/releases` 下载，
附件包含 `AndroidDesktopClock-v1.0.apk` 与 `SHA256SUMS.txt`。

流水线定义见 `.github/workflows/release.yml`：

- **未配置签名密钥**时：构建 debug APK（可安装，但系统会提示来源不受信任）
- **配置了签名密钥**时：构建并使用自有证书签名的 release APK

### 配置自有签名（可选，推荐正式分发时使用）

```bash
# 1. 生成 keystore（只需一次，务必备份，丢失后无法覆盖升级）
keytool -genkeypair -v -keystore release.jks -alias desktopclock \
  -keyalg RSA -keysize 2048 -validity 10000

# 2. 转成 base64
base64 -w0 release.jks > release.jks.b64
```

在仓库 `Settings → Secrets and variables → Actions` 添加 4 个 secret：

| Secret | 值 |
|---|---|
| `KEYSTORE_BASE64` | `release.jks.b64` 的内容 |
| `KEYSTORE_PASSWORD` | keystore 密码 |
| `KEY_ALIAS` | `desktopclock` |
| `KEY_PASSWORD` | key 密码 |

配置后重新推标签即产出签名 release APK。`keystore.properties`、`*.jks` 已在 `.gitignore` 中，不会入库。

> 注意：debug 包与 release 包签名不同，两者**不能互相覆盖安装**，切换时需先卸载。

## 目录结构

```
app/src/main/java/com/example/floatingclock/
├── MainActivity.java           # 设置页：悬浮时钟 / 桌面时钟 双 TAB
├── ClockPrefs.java             # 全部设置项读写 + 时间格式工具
├── ColorPickerDialog.java      # 内置选色盘（HSV 色板 + 亮度/透明度滑条）
├── ClockOverlayService.java    # 悬浮时钟服务（可拖动）
├── DesktopClockActivity.java   # 桌面时钟全屏页面（防烧屏 / 亮度手势 / 信息面板）
├── DesktopConfig.java          # 桌面时钟组件化布局配置与预设管理
├── WeatherCardView.java        # 结构化天气卡片（三日对比 / 逐小时走势）
├── CpuMonitorView.java         # CPU 每核心占用曲线（自定义 View，大小核排布）
└── CpuOverlayService.java      # 悬浮 CPU 监控服务（复用 CpuMonitorView）
```

## 权限

| 权限 | 用途 |
|---|---|
| `SYSTEM_ALERT_WINDOW` | 悬浮时钟、悬浮 CPU 监控 |
| `FOREGROUND_SERVICE` | 悬浮服务声明 |

悬浮相关功能需要在系统设置中授予「显示在其他应用上层」权限，App 内提供跳转入口。

## 说明

- 时间格式为 `SimpleDateFormat` 语法；桌面时钟会自动剔除格式中的毫秒部分（`S`），只按秒刷新。
- CPU 数据全部来自系统公开的 `/proc/stat` 与 `/sys/devices/system/cpu/*/cpufreq/*`，无需 root。
- 大小核判定不写死核心数，而是读取各核心最大频率后，按相邻频率档位之间的相对落差切分，因此可适配 4+4、2+2+4 等不同 SoC。
