# PomodoroTimer —— Compose Multiplatform 版

同一款番茄钟（Miuix 风格）的原生多平台实现：**Android / Windows / Linux / macOS / iOS** 共用一套 Kotlin + Compose UI（`composeApp/src/commonMain`），平台差异收敛在 `expect/actual` 与各平台入口里。

> 产物与 [miuix 官方预览包](https://github.com/compose-miuix-ui/miuix/releases) 对齐：android apk / windows exe / linux x64+arm64 / darwin dmg / ios 未签名 ipa。

## 结构

```
composeApp/
├── src/commonMain/   共用：App.kt（全部 UI 与动画）、State.kt（计时/统计/存储接口）、Theme.kt
├── src/androidMain/  MainActivity + SharedPreferences 存储 + 前台服务/通知
├── src/desktopMain/  main.kt（窗口入口）+ 桌面平台实现
└── src/iosMain/      MainViewController + NSUserDefaults 存储 + UIKit/AudioToolbox 实现
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

**实测结果**（平均差为 0–255 尺度，越小越接近；12 个页面/主题/状态组合，见 `compare_shots.py` 的 PAIRS）：

| 页面              | 平均差      | >32 像素占比  |
| ----------------- | ----------- | ------------- |
| 计时页 · 深色     | 0.74        | 0.51%         |
| 计时页 · 浅色     | 0.81        | 0.43%         |
| 计时页 · 运行中   | 0.78        | 0.46%         |
| 统计页 · 深/浅色  | 1.00 / 0.99 | 0.83% / 0.55% |
| 待办页 · 浅/深色  | 0.51 / 0.54 | 0.31% / 0.38% |
| 待办页 · 有待办项 | 0.68        | 0.34%         |
| 设置页 · 浅/深色  | 1.08 / 0.99 | 0.71% / 0.76% |
| 关于页 · 浅色     | 0.66        | 0.40%         |
| 计时页 · 极光     | 1.37        | 0.53%         |

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
  阴影不再透过半透明卡片，卡片间隙的暗部也与网页一致（极光 1.60 → 1.37，其余主题同步下降）。
- **周分布图**：网页是「渐变药丸柱（18×全圆角 9、180° primary→primary-container、35% 光晕）+ 星期标签」，
  不是灰色方柱 + 日期数字。
- **待办页**：空态/统计行文案、勾选圆（边框圈 → 完成后填充，无对勾）、列表顺序与网页一致。

**已知不适用等价物**：

- `backdrop-filter` 的**模糊分量**仍无逐像素对应（saturate 与嵌套底色的重复合成已复刻）；
  剩余差异集中在元素边缘约 8–16px 的模糊过渡带，以及 Skia 与 Chromium 的渐变抖动（1.37 的残余）。
  自定义背景图模式下同理无法采样图片背层。
- 文字光栅化（Skia vs Chromium）带来的 1px 级描边差异：属于系统渲染器差异，无法消除；
  大色块/描边/圆角颜色在抽样点逐像素相同。
- 关于页 0.66、其余页面 0.5–1.1 的平均差已主要来自文字抗锯齿、渐变抖动与亚像素定位，继续收敛的性价比很低。

**被墙时的推送通道**：`github.com:443` 不可达而 `api.github.com` 正常时，用 `node tools/gh-push.js main` 经 Git Data API 推送（内部自检「远端 tree == 本地 tree」）。
