# PomodoroTimer —— Compose Multiplatform 版

同一款番茄钟（Miuix 风格）的原生多平台实现：**Android / Windows / Linux / macOS / iOS** 共用一套 Kotlin + Compose UI（`composeApp/src/commonMain`），平台差异收敛在 `expect/actual` 与各平台入口里。

> 产物与 [miuix 官方预览包](https://github.com/compose-miuix-ui/miuix/releases) 对齐：android apk / windows exe / linux x64+arm64 / darwin dmg / ios 未签名 ipa。

## 结构

```
composeApp/
├── src/commonMain/   共用：App.kt（全部 UI 与动画）、State.kt（计时/统计/存储接口）、Theme.kt
├── src/androidMain/  MainActivity + SharedPreferences 存储 + 前台服务/通知
├── src/desktopMain/  main.kt（窗口入口）+ 桌面平台实现；resources/app-icon.png 为窗口/任务栏图标
├── src/iosMain/      MainViewController + NSUserDefaults 存储 + UIKit/AudioToolbox 实现
└── icons/            应用图标（app.ico / app.icns / app.png，由同一张 750² PNG 生成）
iosApp/               iOS 的 SwiftUI 壳（XcodeGen 生成 xcodeproj）
```

## 各平台构建

| 平台    | 命令                                                            | 产物                                                                |
| ------- | --------------------------------------------------------------- | ------------------------------------------------------------------- |
| Android | `./gradlew :composeApp:assembleDebug`                           | `composeApp/build/outputs/apk/debug/composeApp-debug.apk`           |
| Windows | `gradlew.bat :composeApp:createReleaseDistributable`            | `composeApp/build/compose/binaries/main-release/app/PomodoroTimer/` |
| Linux   | `./gradlew :composeApp:createReleaseDistributable`              | 同上（`bin/PomodoroTimer`）                                         |
| macOS   | `./gradlew :composeApp:packageReleaseDmg`                       | `…/main-release/dmg/*.dmg`                                          |
| iOS     | macOS + Xcode：`cd iosApp && xcodegen generate` 后 `xcodebuild` | 未签名 `.app`（再打成 `.ipa`）                                      |

iOS 打包（CI 同款流程）：

```bash
cd iosApp
xcodegen generate
xcodebuild -project iosApp.xcodeproj -scheme iosApp -configuration Release \
  -sdk iphoneos -destination 'generic/platform=iOS' -derivedDataPath build \
  CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO build
mkdir -p Payload && cp -R build/Build/Products/Release-iphoneos/iosApp.app Payload/
zip -r PomodoroTimer.ipa Payload
```

## 云端六平台构建

[`.github/workflows/build-multiplatform.yml`](../.github/workflows/build-multiplatform.yml)：

- **手动**：Actions → _Build Multiplatform_ → Run workflow → 从 Artifacts 下载
- **打标签**：`git tag v1.0.0 && git push origin v1.0.0` → 自动创建 Release 并附带全部产物

| Job         | Runner                            | 产物                                         |
| ----------- | --------------------------------- | -------------------------------------------- |
| android     | ubuntu-latest                     | `PomodoroTimer-android-universal-<ver>.apk`  |
| windows     | windows-latest                    | `PomodoroTimer-windows-x64-exe-<ver>.zip`    |
| linux-x64   | ubuntu-latest                     | `PomodoroTimer-linux-x64-bin-<ver>.zip`      |
| linux-arm64 | ubuntu-24.04-arm                  | `PomodoroTimer-linux-arm64-bin-<ver>.zip`    |
| macos       | macos-14                          | `PomodoroTimer-darwin-arm64-dmg-<ver>.dmg`   |
| ios         | macos-14（xcodegen + xcodebuild） | `PomodoroTimer-ios-arm64-unsigned-<ver>.ipa` |

## 版本组合（必须成套）

| 组件                         | 版本                                                   |
| ---------------------------- | ------------------------------------------------------ |
| Kotlin                       | 2.4.10                                                 |
| Compose Multiplatform        | 1.12.0                                                 |
| Miuix（miuix / miuix-icons） | 0.8.8                                                  |
| AGP / Gradle                 | 9.1.0 / 9.3.1                                          |
| kotlinx-datetime             | 0.7.1（与 material3 1.9.0 的传递依赖一致，**勿降级**） |
| JDK                          | 21（`jvmToolchain(21)`）                               |
| compileSdk / minSdk          | 37 / 26                                                |

## 已知说明

- iOS / macOS 产物**未签名**：iOS 需自行重签（如 Sideloadly / AltStore），macOS 需右键“打开”绕过 Gatekeeper。
- Windows / Linux 产物是 jpackage 的 app-image（自带运行时），解压即用；未做代码签名。
- Android APK 为 debug 签名（预览用途）。
- `Modifier.blur` 需要 Android 12（API 31）以上：更低版本会退化为无模糊（卡片阴影、环发光、
  标题栏背景会变成纯色/无阴影）。其余平台不受影响。
- `org.gradle.java.home` 已移除：本机构建请保证 `JAVA_HOME` 指向 JDK 21。
- 自定义背景图：支持 `https://` 图片链接与 `data:image/…;base64`（下载/解码为三端 `expect/actual`，
  Android 已声明 `INTERNET` 权限）；背景图生效时卡片按网页 `[data-bg-active]` 的百分比切毛玻璃
  （仍无 backdrop 模糊——Compose 无背层采样，与极光主题同款取舍）。本地选图与 API 换图暂未接入；
  明文 `http://` 受系统 cleartext/ATS 限制，请用 `https://`。

## 与网页版的一致性验证（原生 UI 对齐）

目标：六平台原生版与 `index.html`（以及内嵌同一份网页的安卓版）观感一致。为此建了一套**可复现的像素比对闭环**：

```bash
# 1) 原生侧：离屏渲染各页面（ImageComposeScene，无需窗口），默认 1280×800dp @2 → 2560×1600
./gradlew :composeApp:screenshots

# 2) 网页侧：headless Edge + CDP 截图（复用 android 内置字体走本地 HTTP，渲染与安卓实机一致）
node tools/webshot.js timer dark  "%TEMP%\shot\web-timer-dark.png"
node tools/webshot.js running dark "%TEMP%\shot\web-timer-running-dark.png"   # 运行中状态
node tools/webshot.js todos-items light                                       # 带待办项
node tools/webshot.js about light                                             # 关于页

# 3) 逐像素比对：平均差 / 超阈像素占比 / 条带定位 + 热力图与并排图
python tools/compare_shots.py composeApp/build/screenshots "%TEMP%\shot" "%TEMP%\diff"

# 4) 窄屏（网页 @media ≤760/≤360）：同一套流程换视口即可
./gradlew :composeApp:screenshots -PshotW=786 -PshotH=1704   # 393×852dp @2
./gradlew :composeApp:screenshots -PshotW=720 -PshotH=1600   # 360×800dp @2
node tools/webshot.js timer dark "%TEMP%\web-timer-dark.png" 393 852 2
```

**量测工具**（对齐排查用，`tools/` 下）：

| 工具                            | 用途                                                                 |
| ------------------------------- | -------------------------------------------------------------------- |
| `web_metrics.js`                | 导出网页端每个元素的 rect/字号/行高/padding 与文本墨迹盒（对齐基准） |
| `align_report.py`               | 分块搜索最佳整体位移，定位「哪一块错位了几像素」                     |
| `rulers.py`                     | 沿列/行找元素边界并自动配对，报告每处偏移                            |
| `text_rows.py` / `text_cols.py` | 扫描横带/竖带内文字墨迹行（列），逐条对比基线（支持亮色模式）        |
| `profile.py`                    | 沿行/列输出两图彩色剖面，查径向光晕、描边 1px 级差异                 |
| `ink_box.py` / `bright_box.py`  | 量文字/图标的实际墨迹范围（忽略背景与低对比发光）                    |
| `crop_pair.py`                  | 同区域裁剪、上下并排，放大对比细节                                   |
| `sample.py`                     | 多点取色对比                                                         |

**实测结果**（平均差为 0–255 尺度，越小越接近；桌面 1280×800@2，12 个页面/主题/状态组合，见 `compare_shots.py` 的 PAIRS）：

| 页面              | 平均差      | >32 像素占比  |
| ----------------- | ----------- | ------------- |
| 计时页 · 深色     | 0.48        | 0.23%         |
| 计时页 · 浅色     | 0.49        | 0.21%         |
| 计时页 · 运行中   | 0.55        | 0.22%         |
| 统计页 · 深/浅色  | 0.41 / 0.38 | 0.25% / 0.24% |
| 待办页 · 浅/深色  | 0.24 / 0.29 | 0.16% / 0.20% |
| 待办页 · 有待办项 | 0.40        | 0.21%         |
| 设置页 · 浅/深色  | 0.46 / 0.51 | 0.37% / 0.38% |
| 关于页 · 浅色     | 0.75        | 0.44%         |
| 计时页 · 极光     | 1.09        | 0.22%         |

窄屏（网页 `@media(max-width:760px)` / `(max-width:360px)` 全部规则已复刻）：

| 视口        | 计时深/浅  | 计时极光 | 统计浅/深  | 待办浅/深  | 设置浅 | 关于浅 |
| ----------- | ---------- | -------- | ---------- | ---------- | ------ | ------ |
| 393×852@2   | 1.31/1.35  | 2.07     | 0.75/0.79  | 0.69/0.72  | 1.41   | 2.48   |
| 360×800@2   | 1.27/1.31  | —        | 0.82       | 0.79       | 1.62   | 2.92   |

窄屏平均差高于桌面是**信噪比**差异：视口面积只有桌面的 1/3，同一批文字/描边占了更大像素比例，
抗锯齿与 1px 取整误差被放大；按内容区归一后两者处在同一量级（如设置页 0.76 vs 1.41、
待办页 0.39 vs 0.73）。统计/待办在窄屏已回到 0.7–0.8，主要残余是文字光栅化。

对齐过程中修正的**系统性差异**（都已落实）：

- **行盒高**：CSS `line-height: normal` 实测比 Compose 默认度量高 1–2px（如 15px 字号 19 vs 18），
  且中文回退字体（雅黑/MiSans）比 Inter 高约 5% —— 统一用 `Theme.kt` 的 `normalLine(size)` 显式指定，
  纯数字文本用 `latinLine(size)`（Inter 1.21）；消除了设置页/关于页向下逐行累积的 5–6px 漂移。
- **行盒压缩与负半行距**：CSS `line-height` 小于字体自然行高时（图标 `line-height:1`、时间 `line-height:1`），
  Compose 行盒仍会被撑到字体自然高度（图标 ≈1.2em、Inter ≈1.21em），且基线位置比 CSS 低一个「半行距」。
  统一做法：`Symbol()` 用定高盒 + 基线按 `(natural-1)/2` 上移；时间文本同理 + 半行距补偿（0.105em）。
  这一项曾让侧栏每项累计漂移 5px、计时中心块错位 16px。
- **border-box 边框占位**：网页卡片/统计格/胶囊/顶栏的 1px 边框会占掉内边距（内容从 padding+1 开始、
  卡片因此高 2px），Compose 的 `Modifier.border` 不占位 —— 在各容器显式 +1 补齐（`.card`、
  `.stat-cell`、`.tone-chip`、`.topbar` 的 border-bottom）。
- **组件尺寸按 CSS 内容盒推导**：Segment 按钮 10+行盒+10（容器 46，不再是固定高度）、选中指示条相对
  整个按钮盒定位（`bottom:6px`，而非内容盒）、步进器 gap 4、音色 chip 边框恒存（选中透明，否则矮 2px）、
  会话圆点 10px + 光晕不占布局、卡片标题 `<strong>` 与网页同为 700（只改颜色）。
- **行盒按像素取整（钉住）**：Skia 会把显式行高的行盒按整数像素向上取整，多出的 0.5dp 会逐行累计
  （统计页 3 个格子 → 卡片高 3px、窄屏段选胶囊高 1px、待办页整卡高 10px）。对像素敏感的固定行高
  文本（`CardTitle` 19dp、统计格标签 16dp、段选按钮 16/18dp）用 `Modifier.height` 钉住行盒。
  这一项把统计页 1.00 → 0.41、待办页 0.51 → 0.24（桌面），窄屏同样从 1.6/1.2 → 0.8/0.7。
- **字重与图标字号继承**：`CardTitle` 的 `<strong>` 与网页同为 700（只改颜色）；段选按钮行高与
  内边距按网页 border-box 推导；图标字体的基础 `font-size: 24px` 会覆盖容器字号（`.setting-row`/
  `.icon-btn` 的图标实测 24 而非容器 22）。
- **窄屏响应式**：`LocalCompact`（<760dp）与 `LocalScreenWidth` 提供断点，逐条复刻
  `@media(max-width:760px)`：顶栏 14/18/10、内容 18/16/32、卡片 20/r18、主按钮 56/26、
  统计格 1 列（间距 16）、底栏 `padding 6 + safe`/`gap 6`/item 宽 `min(80,(W−18)/4)`；
  `@media(max-width:360px)`：标题 22、环 `clamp(190,58vw,260)`、控件 gap 10、
  段选 13px/9px、统计格 15/24。底栏条目的实际中心用「剩余空间四等分（两端各 1/8）」复刻
  `justify-content:space-around`，与网页逐项对齐。
- **手机横屏**（`LocalShortScreen`：高 <500dp 且宽 > 高，对应 `@media(max-height:500px) and
  (orientation:landscape)`）：顶栏 10/18/8、标题 20、内容 12/16/18、计时卡内边距 18/18/20、
  环 `min(210px,46vh)`、控件 gap 10、卡间距 22（`.page > .card + .card`）、计时卡内 gap 16。
  `.timer-time` 在所有断点都是 `clamp(44px, 13vw, 64px)`（原先桌面按环径等比，
  横屏会得到 44 而非 64）。headless 截图的 `(orientation: landscape)` 需要
  `Emulation.setDeviceMetricsOverride` 里的 `screenOrientation`，`tools/webshot.js` 已带上。
- **平板**（`LocalTablet`：761–1024dp）：侧栏 76dp、条目宽 60dp、内容列 `max-width:860dp`。
- **待办输入行**：网页 `<input>` 是 border-box 46px（12+12 内边距 + 1+1 描边 + 20 行盒），
  原生把装饰盒定高 46dp；空态与 `.todo-stats` 的 `margin:14px 2px 6px` 在高度 0 时**折叠成 14**
  （原生原先多加了 6dp），且占位符必须单行（网页 `<input>` 永不换行）。
- **关于页 flex 收缩**：`.about-item` 的标签/地址按 max-content 等比收缩、`word-break: break-all`，
  窄屏用 `TextMeasurer` 量出两者宽度作为 Compose `weight`，复刻逐行换行位置；桌面仍用定高行。
- **安全区**：顶栏/底栏/关于页/迷你计时器分别加 `WindowInsets.systemBars`（对应网页
  `env(safe-area-inset-top/bottom)`）；桌面与离屏截图 insets 为 0，不影响像素比对。
- **Android 平台适配**（对齐同仓库 `android/` 里的 WebView 版）：
  - 根节点强制 `Density(fontScale = 1f)`，对应 WebView 的 `textZoom = 100`：系统字号调大时网页不变形，原生也不变形。
  - `PlatformSystemBars(c.dark)` 同步状态栏/导航栏图标明暗（WebView 版通过 bridge 注入）；
    `enableEdgeToEdge()` + 根背景铺满，状态栏区域的颜色/内容与网页一致。
  - `res/` 补齐主题与图标：`windowBackground` = 网页 `--miuix-background`（浅 `#f4f5f7` / 深 `#1a1a1c`）避免首帧白闪；
    自适应图标（蓝底 + 白色计时器）与 WebView 版同款；`values-v31` 设置
    `windowSplashScreenBackground/AnimatedIcon`，Android 12+ 系统启动画面不再出现默认白底机器人。
  - `configChanges` 与 WebView 版一致（含 `uiMode|density|fontScale|smallestScreenSize`）：
    切换深色模式/字体大小/旋转不重建 Activity（`isSystemInDarkTheme()` 仍会驱动重组）。
- **动效曲线**：网页四条缓动在 `Theme.kt` 里一一对应 —— `--spring (0.34,1.56,0.64,1)`、
  `--ease (0.4,0,0.2,1)`、`--nav-ease (0.4,1.2,0.95,0.97)`、波纹 `(0.22,1,0.36,1)`；
  所有 `transform` 过渡改用 `tween(时长, easing = CssSpring)` 而非 Compose `spring()`
  （后者的过冲曲线与浏览器 `cubic-bezier` 不同，观感偏“弹”）。
- **模式色**（网页 `updateModeUI()`）：进度弧、环内呼吸点、迷你计时器环随模式取色 ——
  专注 = `primary`、短休息 = `#36D167`、长休息 = `#00BCD4`；`.ring-progress` 的 stroke
  有 0.5s `var(--ease)` 过渡，原生用 `animateColorAsState(tween(500, CssEase))` 复刻。
  截图闭环里加了 `desktop-timer-short/-long` 用例（`node tools/webshot.js timer-short|timer-long`）。
- **极光/背景图淡入**：`.aurora-bg { opacity: 0 → 1, .8s var(--ease) }`（切走时保留最后一帧淡出），
  `.bg-image { opacity 0 → 1, .6s var(--ease) }`；截图时（`debugFreezeAnim`）直接落到终态，
  保证与网页 `animation:none` 的基准帧可比。
- **主题切换是「渐变」而不是「瞬切」**：网页给 `body`（.55s）、`.card`（.4s）、`.topbar`（.5s）、
  `.rail`（.4s）、按钮/段选（.3–.35s）各自挂了 `background-color/color/border-color` 过渡；
  原生在 `PomodoroTheme` 里用一份调色板插值统一复刻（`lerpColors`，0.45s `var(--ease)`，
  取各元素时长的中间值），结构位（`dark`/`aurora`/`bgActive`）仍立即生效——与网页
  `[data-theme]` 切换时的结构变化一致。连续切换时从「当前可见色」继续过渡，不会回跳。
- **视口相关的字号/进度过渡**：`.timer-time { transition: font-size .3s var(--ease) }`
  （缩放窗口时环内数字平滑变化）与 `.mini-progress { transition: stroke-dashoffset .3s linear }`
  都用 `animateFloatAsState` 复刻。
- **涟漪范围与网页一致**：网页只给
  `.btn / .icon-btn / .segment button / .rail-item / .stepper button / .color-swatch / .tone-chip / .upload-btn / .switch`
  挂涟漪；待办勾选（`.todo-check`）和删除按钮（`.todo-del`）在网页里只有按下缩放，原生已同步去掉这两处的涟漪。
- **Miuix 原版动画的取舍**：库里的 `MiuixIndication`（弹簧涟漪）、`SinkFeedback`（按下「沉降」）、
  `SpringEngine` 与网页的 CSS 曲线并不等价，直接用会让动效偏离网页，因此保持
  「能对上就用 CSS 复刻」；只有在对不上的地方才考虑切 Miuix 原版（目前所有可见动效都已对上）。
  若后续要做系统「减少动态效果」适配（网页 `prefers-reduced-motion` 会整体关掉动画），
  再统一改走 Miuix 的 spring 系列会更省事。
- **逐条复刻的关键帧**：`pageIn .35s`（页面容器）、`cardIn .5s + 20ms`（卡片内容，
  时钟卡片排除环本身）、`popIn .6s`（计时环）、`growbar .3s`（段选下划线，从左侧 scaleX）、
  `iconPop .42s`（开始/暂停图标：scale .5→1.18→1、rotate −25°→8°→0、opacity 0→1）、
  `bump .5s`（统计数值变化时 scale→1.22）、`barGrow .6s + 每列 .07s`（周柱从底部展开）、
  `aboutPop .5s/.05s`、`aboutUp .45s/.1s`、`aboutUp .5s/.16s(+.22s)`（关于页 logo/名称/卡片）、
  `themeWash .75s`（主题洗刷：径向 primary 42%、scale .5→1.4、opacity 0→.55→0）、
  `stroke-dashoffset .3s linear`（环进度）。
- **触感反馈**：按钮/侧栏/步进器/开关/音色胶囊/待办勾选按下缩放按各自 CSS 时长与弹簧曲线；
  另加 MIUI 波纹（`Modifier.miuixRipple`：按下点半径 = 元素长边/2、scale 0→3、
  opacity .5→.25（60%）→0，0.7s），只在 Initial 阶段观察指针事件、不消费，不影响点击。
- **悬停态（桌面指针设备）**：网页的 `:hover` 规则逐条复刻 —— `.icon-btn`/`.rail-item` 底色换成
  container-high 并上移 1px（极光下用白色 12%）、`.btn-ghost`/`.stepper button` 换 container-highest
  （极光 16%）、`.btn-primary` 用 `brightness(1.05)`（逐通道乘系数，`brighten()`）、
  `.stat-cell` 上移 2px、`.todo-item` 上移 1px、`.todo-del` 换 danger-soft + error 文字、
  `.color-swatch` 放大 1.1。触摸端 `collectIsHoveredAsState` 恒为 false，不影响手机观感。
- **启动页**：与网页 `runLoading()` 同节奏 —— 1600ms 线性走满、字体条延后 15% 起步
  （`(t−0.15)/0.85`）、logo `breathe 2.2s var(--ease)` 呼吸、结束 0.4s `var(--ease)` 淡出。
- **超出回弹**（设置里的「超出回弹」开关真正生效）：对应网页 `enableOverscrollStretch()` ——
  触摸拖到顶/底后继续下拉时，按 `d = over·cap/(cap+over)`（cap = .55×视口高）做
  一半位移 + `scaleY(1+d/h)` 拉伸（原点在被拖的那一侧），松手 0.5s 弹簧回弹；关闭开关立即收回。
- **Toast**：底部 36、`translateY(20→0)` + opacity 0→1，0.35s 弹簧；停留 2400ms（同网页 `setTimeout`）。
- **应用图标**：`composeApp/icons/app.ico|app.icns|app.png`（同一张 PNG 生成）供 jpackage 使用，
  `windows/macOS/linux` 的 `iconFile` 已接；桌面窗口/任务栏图标由 `src/desktopMain/resources/app-icon.png`
  在运行时设置（`Window(icon = …)`）；Android 用自适应图标（蓝底 + 白色计时器，与安卓 WebView 版同款）。
- **桌面通知**：新增 `requestNotificationPermission` / `platformNotify` 的 expect/actual。
  网页只在用户打开「桌面通知」时调 `requestNotify()`，未授权不点亮开关 —— 原生同逻辑：
  Android 用 `POST_NOTIFICATIONS` 运行时权限（发送走 `applicationContext`，Activity 回收后仍可发）；
  桌面用 `SystemTray` 气泡；iOS 用 `UNUserNotificationCenter`。完成时的文案与网页一致
  （专注完成/休息结束），跳过（`skip`）不发通知。
- **关于页交互**：行按下时底色 `rgba(0,0,0,.05)`（深色/极光 `rgba(255,255,255,.06)`）；
  外链行（我的 GitHub / 六个开源组件）用系统浏览器打开（`openExternalUrl`：Android `ACTION_VIEW`、
  桌面 `Desktop.browse`、iOS `openURL`）；`设计参考`/`开源许可`/`第三方许可` 打开网页同款弹窗
  （遮罩 `rgba(0,0,0,.42)` 0.25s `var(--ease)`、卡片 max-width 430 / max-height 76vh / radius 22 /
  padding 22 / gap 14、scale .92→1 0.3s `var(--spring)`、点遮罩或「关闭」收起，正文逐字与网页一致）。
- **层级与快捷键**：迷你计时器(40) 低于关于页(45)、Toast(50) 高于关于页、洗刷层(900) 最高；
  桌面空格开始/暂停（输入框聚焦时由输入框消费）、依次 Esc 关弹窗→关关于页；
  安卓返回键同样「弹窗 → 关于页」逐级关闭（`PlatformBackHandler`）。
- **进度环发光**：CSS 是 `drop-shadow(0 0 10px primary@60%)`，原生改用同弧线 13dp 高斯模糊层
  （`Modifier.blur`，Android 12 以下为空操作）；环内环境光按 `radial-gradient(26% → transparent 70%,
circle=最远角, opacity .5→.85)` 精确定参；截图时两边把呼吸动画冻结到同一基准相位
  （`State.debugFreezeAnim` + `webshot.js` 注入 `animation:none`）。
- **主按钮阴影**：CSS `box-shadow: 0 10px 24px primary@35%` 只有向下偏移、无环境光；
  Compose `Modifier.shadow` 会四向发光 —— 改用「偏移 10dp + σ12 模糊层」叠加复刻。
- **渐变的梯度轴**：CSS `linear-gradient(Adeg, …)` 的轴长是 `w·|sinA| + h·|cosA|`、过中心
  （不是对角线长度）；停在百分比上的中间色标也要一致。关于页背景曾因轴长算短导致整页偏色 6→0.66；
  极光 180% 画布的基础底色同样改用该轴（此前按 (0,0)→(w,h) 近似，非正方形画布上方向/长度都不对）。
- **极光背景**：按 `.aurora-bg > i` 的 180% 画布 + 4 个椭圆光斑（`58% 58% at 18% 22%` …）+
  `steps(130)` 漂移逐项复刻（截图脚本把网页动画冻结在同一相位）；`color-mix(X N%)` 一律按
  **alpha × N%** 换算（曾误用 alpha 覆盖，极光下 rail/迷你计时器整块偏亮）。
- **极光画布锚点（requiredSize 居中语义）**：Compose 的布局对超出约束的内容会在约束框内**居中**
  （偏移 −(lw−w)/2, −(lh−h)/2），而 CSS 图层是左上锚定 —— 180% 极光画布曾因此整体错位（20.59 → 4.09）。
  改为在根部算好场的位置（`AuroraFieldSpec`），各层用带锚定的 `drawBehind` 直接绘制。
- **drawRect(brush) 默认节点尺寸**：不传 `topLeft`/`size` 时按 DrawScope 所属节点大小铺底 ——
  全屏基座上没问题，但卡片/顶栏这类小节点里 180% 场的暗底只画了一角，4 个光斑悬空叠加 → 整屏亮雾
  （极光 17.98 → 2.73 的最后一处）；极光场一律显式 `size = (wPx, hPx)`。
- **backdrop-filter 的 saturate 模拟**：CSS 的 `backdrop-filter: saturate(N)` 会对元素背后已合成的
  背层增艳。Compose 无背层采样，但极光场是纯函数 —— 根部先按漂移相位把场光栅化成**屏幕空间位图**
  （漂移每跨一步重建一次），玻璃容器用 `auroraBackdrop(N)` 对齐绘制该位图 + saturate 色彩矩阵
  （0.213/0.715/0.072 系数），语义等同「先合成背层、再整层过滤」；并 `clipToBounds` 裁到容器自身
  （对应 backdrop 只作用于元素背后区域）。
  卡片 1.5 / 侧栏 1.6 / 顶栏 1.8 / 统计格 1.4 与网页一致（曾逐笔过滤，因 alpha 合成顺序差异偏色，改为位图后 2.73 → 1.94）。
- **嵌套 backdrop-filter 的背层重复**：卡片内的 segment / stat-cell / btn-ghost 在 Chromium 里
  采样到的背层会**再叠一次卡片底色**（实测：子元素背层 = 卡片可见像素 + 卡片底色；即使
  `backdrop-filter: blur(0)` 也会触发）。原生用 `LocalCardGlass` 把卡片底色传给子级玻璃：
  `auroraBackdrop` 合并「祖先 × 自身」的 saturate，并把卡片底色再画两遍。修正后段选控件
  从偏暗 12 级回到 ±1（极光 1.89 → 1.60）。
- **卡片阴影**：CSS `box-shadow` 有明确的向下偏移与 blur（极光 `0 12px 36px`、其余 `0 8px 24px`），
  且**不会画在 border box 内**；Compose `Modifier.shadow` 无偏移、实测强度几乎不可见。改为
  「偏移 + 高斯模糊（σ = CSS blur / 2）+ 用 `ClipOp.Difference` 挖掉卡片本体」的独立图层，
  阴影不再透过半透明卡片，卡片间隙的暗部也与网页一致（极光 1.60 → 1.35，其余主题同步下降）。
  段选按钮选中态的同款阴影（极光 `0 2px 10px rgba(0,0,0,.28)`）也复用该图层。
- **周分布图**：网页是「渐变药丸柱（18×全圆角 9、180° primary→primary-container、35% 光晕）+ 星期标签」，
  不是灰色方柱 + 日期数字。
- **待办页**：空态/统计行文案、勾选圆（边框圈 → 完成后填充，无对勾）、列表顺序与网页一致。

**已知不适用等价物**：

- `backdrop-filter` 的**模糊分量**仍无逐像素对应（saturate 与嵌套底色的重复合成已复刻）；
  剩余差异集中在元素边缘约 8–16px 的模糊过渡带，以及 Skia 与 Chromium 的渐变抖动（1.35 的残余）。
  自定义背景图模式下同理无法采样图片背层。
- 文字光栅化（Skia vs Chromium）带来的 1px 级描边差异：属于系统渲染器差异，无法消除；
  大色块/描边/圆角颜色在抽样点逐像素相同。
- 桌面 0.24–0.75（极光 1.09）已主要来自文字抗锯齿、渐变抖动与亚像素定位；窄屏 0.7–1.6
  （关于页 2.5）同理，且视口小、信噪比更低。关于页残余集中在 96px logo 阴影、渐变标题墨迹与
  `word-break: break-all` 的换行点（Compose 无对应的「任意字符可断行」策略）。
- `cardIn` 的逐子元素延迟（网页按 `nth-child` 给 .02/.06/.1s）在 Compose 里无法对
  `ColumnScope` 的子项逐个加图层，改为**整块**入场（时长/曲线/位移量一致，观感差别在一次
  20–100ms 的错落）；时钟卡片仍按网页规则把环排除在外、单独 `popIn`。
- iOS 的 `UNUserNotificationCenter` 代码只能在 macOS 上编译验证（本机 Windows 无法构建 iOS 目标）。

**被墙时的推送通道**：`github.com:443` 不可达而 `api.github.com` 正常时，用 `node tools/gh-push.js main` 经 Git Data API 推送（内部自检「远端 tree == 本地 tree」）。
