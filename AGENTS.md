# ClassSchedule 项目 · Agent 工作说明

> 详细进度与踩坑记录在 `PROGRESS.md`（**仅本地，不入库**）。项目根 = 本文件所在目录。

## 项目

Android 课表 App：登录学校教务系统 → 自动识别课表 → 本地保存 → 每次打开静默更新。
包名 `com.lorat.classschedule`，Kotlin + Jetpack Compose（navigation3），minSdk 26。

## 环境与命令

```powershell
$env:JAVA_HOME="C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot"
cd <项目根>

.\gradlew.bat :app:assembleDebug --console=plain        # 构建
.\gradlew.bat :app:testDebugUnitTest --console=plain   # 免模拟器回归：42 个用例
.\gradlew.bat :app:assembleRelease --console=plain     # 发布包（R8 + 资源压缩）

$adb="C:\Users\Lorat\Android\Sdk\platform-tools\adb.exe"   # 模拟器 AVD: classschedule
& $adb install -r app\build\outputs\apk\debug\app-debug.apk
& $adb shell am start -n com.lorat.classschedule/.MainActivity
& $adb shell screencap -p /sdcard/s.png; & $adb pull /sdcard/s.png .\s.png   # 截图必须走 sdcard
& $adb shell input motionevent DOWN 595 1089; Start-Sleep -Milliseconds 900; & $adb shell input motionevent UP 595 1089  # 长按
```

识别逻辑免模拟器回归：`tools/js-harness/README.md`（10 个用例，含脱敏的教务接口响应样例）。

## 版本与发布（由 Agent 管理）

版本号由 Agent 负责命名，规则固定下来：

- **语义化 `MAJOR.MINOR.PATCH`**，1.0 之前一律 `0.x.y`：
  - `MINOR`：一个功能批次（例如 0.3.0 = 分享长图 + English + 顶栏收起）；
  - `PATCH`：只修 bug / 文案 / 性能，例如 `0.3.1`；
  - `MAJOR`：1.0 起出现破坏性数据或 UI 大改才动。
- **`versionCode` 每次发布 +1**（等于第几个发布），与 tag 顺序一致；现在 0.3.0 / versionCode 3。
- 改版本只改 `app/build.gradle.kts` 的 `versionCode` / `versionName`；关于页从 `PackageManager` 读，不用改。
- 发布流程：单测 + release 装机自测 → 改版本号 → 更新 `PROGRESS.md` → commit（中文）→
  `git tag vX.Y.Z` → push（分支 + tag）→ `gh release create vX.Y.Z app/build/outputs/apk/release/app-release.apk --title vX.Y.Z --notes ...`
- Release 正文用中文，按「新增 / 优化 / 修复」分组；APK 只含 `arm64-v8a` + `x86_64`。

## 约定

- 临时文件（`C:\Users\Lorat\_*`、本地服务、临时截图）用完清理。
- 测试会改写 App 内保存的学校配置，**测完必须还原**（空配置 JSON 见 `PROGRESS.md`）。
- 源码快照压到 `snapshots\ClassSchedule_<日期>.zip`；`snapshots/` 与 `PROGRESS.md` 已 gitignore。
- **库里不许出现真实学校信息**（校名、域名、老师姓名、教室号、真实课表截图）——一律用「示例大学 / 张老师 / A101」这类占位。
- 改完代码：`assembleDebug` 通过 → 装机自测 → 追加 `PROGRESS.md` → `git commit`（中文信息）。
- 风格：跟随现有代码，不引入无关依赖，不写多余注释。

## 关键实现（改代码前先看这几条）

- 翻周：标题 `‹ 第N周 ▾ ›` 单步 + 课表区左右滑动（一页一周）。滑动是**自己实现的**
  （`detectHorizontalDragGestures` + `userScrollEnabled = false`）：Pager 自带「拖动 + 惯性」会一次翻两页。
- 本地日志：`data/AppLog.kt`（只写文件、不联网）。位置 `/sdcard/Android/data/<包名>/files/logs/classschedule-<日期>.log`，
  按天一个、保留 7 个；启动时写会话头（版本/机型/WebView）、内置浏览器的报错与 DOM 探针、静默更新与导入结果，
  并捕获未处理异常的堆栈。**不写课程名/老师/地点**；网址经 `redactUrl` 只留 `scheme://host/第一段/…`（查询串会丢掉）。
  纯函数（`logFileName` / `redactUrl` / `filesToDelete`）有 `AppLogTest` 覆盖。
- 长按课表空格子可直接按该格星期/节次新建课程；新课程颜色默认「随机彩色」（`colorIndex = -1`，按课程名取色）。
- 「隐藏非本周课程」开关在 `⋯` 面板和「课表设置 → 课表外观」两处，都写 `showOtherWeeks`。
- 图片识别：`data/OcrTimetable.kt` + `ui/imports/ImageImportScreen.kt`，ML Kit 离线中文 OCR。
  要点：用 **element（词级）坐标**、小图先 `ceil(2000/长边)` 放大、表头取「同一行里星期最全」的一组、
  节次刻度切行带、同格多课按「记录收全」切分、无节次刻度时按纵坐标聚类兜底。改这里必跑 `OcrTimetableTest`。
- 打包：`abiFilters` 只留 `arm64-v8a` + `x86_64`（OCR 的 .so 每 ABI 6~11MB）；release 开 R8 + 资源压缩
  （keep 规则 `app/proguard-rules.pro`，`android.enableR8.fullMode=false`）。**改完必须装 release 实测**。
- 黑夜模式：窗口底色 `values-night/themes.xml`；小部件颜色 `@color/widget_*` + `values-night/colors.xml`。
- 桌面小部件用 RemoteViews，**不能用 `<View>`**（色条用 `TextView`）。

## 已知限制

多课表切换、`.ics`/分享口令导出、其它系统的「大节」兜底、更多小部件样式均未做。
校园网直连在本机不通，「App 自动更新」未在真实网络下复现。