# ClassSchedule 项目 · Agent 工作说明

> 本文件供编码 Agent（Codex / DeepSeek Harness / Claude 等）自动读取。
> **详细进度、原理与背景全在 `PROGRESS.md`，动手前先读它。**
> 项目根 = 本文件所在目录（与 `.git` 同级）；`AGENTS.md`/`CLAUDE.md` 会从项目根向下逐级加载。

## 项目是什么

Android 课表 App：任意学校教务系统登录 → 自动识别课表 → 本地导入 → 每次打开自动更新。
包名 `com.lorat.classschedule`，Kotlin + Jetpack Compose（navigation3）。

## 环境

| 项 | 值 |
|---|---|
| JDK | `C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot`（构建前设 `$env:JAVA_HOME`） |
| Android SDK | `C:\Users\Lorat\Android\Sdk` |
| adb | `C:\Users\Lorat\Android\Sdk\platform-tools\adb.exe` |
| 模拟器 AVD | `classschedule`（通常 `emulator-5554`，1080x2400） |

## 常用命令（PowerShell）

```powershell
$env:JAVA_HOME="C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot"
cd C:\Users\Lorat\ClassSchedule

.\gradlew.bat :app:assembleDebug --console=plain          # 构建
.\gradlew.bat :app:testDebugUnitTest --console=plain     # 免模拟器回归：CSV/周次/进度/小部件，20 个用例

Start-Process "C:\Users\Lorat\Android\Sdk\emulator\emulator.exe" -ArgumentList '-avd','classschedule'   # 需要可视化时
$adb="C:\Users\Lorat\Android\Sdk\platform-tools\adb.exe"
& $adb wait-for-device
& $adb install -r app\build\outputs\apk\debug\app-debug.apk
& $adb shell am start -n com.lorat.classschedule/.MainActivity

# 截图：必须走 sdcard，PowerShell 的 `>` 重定向会破坏二进制
& $adb shell screencap -p /sdcard/s.png; & $adb pull /sdcard/s.png .\s.png; & $adb shell rm /sdcard/s.png

# 长按（`input swipe` 模拟不出长按，用 motionevent）
& $adb shell input motionevent DOWN 595 1089; Start-Sleep -Milliseconds 900; & $adb shell input motionevent UP 595 1089
```

识别逻辑免模拟器回归：`tools/js-harness/README.md`（9 个用例，含真实教务接口响应 `i_exesac`）。

## 约定（务必遵守）

- 调试临时文件（`C:\Users\Lorat\_*`、本地 python 服务、无头 Chrome、临时截图）用完清理。
- 测试会改写 App 内保存的学校配置，**测完必须还原**（空配置 JSON 见 `PROGRESS.md` §7）。
- 快照：源码压到 `snapshots\ClassSchedule_<日期>.zip`；`snapshots/` 与 `PROGRESS.md` 已被 `.gitignore` 忽略（仅本地保存）。
- 改完代码：`assembleDebug` 通过 + 装机自测通过 → 在 `PROGRESS.md` 追加记录 → `git commit`。
- 风格：跟随现有代码，不引入无关依赖，不写多余注释。

## 接手步骤（给下一个 Agent）

1. 读 `PROGRESS.md`：当前状态、5 级识别管线原理、模拟器坐标、已知限制、收尾约定。
2. 读本文件的「环境 / 命令 / 约定」。
3. `git log --oneline`、`git status -s` 看最近改动与未提交内容。

## 当前状态速览（2026-10-13）

- **外观与交互已按 WakeUp课程表 重做**（官方规格摘录 `docs/wakeup-official-spec.md`，实现截图 `docs/screenshots/`）：
  主界面 = 日期大标题 + `第N周▾` + 右上自绘 `⊕ ↓ ↑ ⊙` + 日期栏 + 左侧时间轴 + 彩色课程格子；
  课程支持多时间段（靠 `Course.groupId` 分组）、单双周、冲突平分；课表外观约 20 项可调且即时生效。
- 页面：主界面 / 添加·编辑课程 / 已添课程 / 课表设置（数据+外观）/ 上课时间 / 全局设置 / 关于 /
  导入课表（教务·CSV·备份 JSON·HTML·粘贴）/ 导出（备份 JSON、CSV）。
- **已接上真实教务接口**（示例大学，教务处 SaaS）：页面里只有一个空 table，数据全部来自
  `POST /Teacher/TimeTableHandler.ashx`（`action=getTeacherTimeTable`）的 JSON，
  字段 `ClassTimePatterns`(节号×5 分钟) / `CurrentWeekIndex` / `Data[]{LUName,FullName,Building,Classroom,
  WeekStart,WeekEnd,WeekInterval,TimeSlotStart,TimeSlotEnd,OnMonday..Sunday,StartDay}`。
  识别器里的 `saas`/`kbExesac` 分支直接消费它：一次拿到**全学期 52 条**每次课 + 精确节次 + 单双周 +
  **教务自报的当前周与开学日期**（`applyImport` 会落库并锚定当前周）。回归用例：`tools/js-harness` 的
  `i_exesac`（内联真实响应，脱敏）。
- 课表渲染规则：同格同名的多段周次合并成一格；本周的课优先，非本周的课只在不与本周重叠时绘制；
  本周真正冲突才左右平分并标三角。非本周的课=整格降透明度+格子底部 `非本周` 纯文字标签，
  `⋯` 面板里有「隐藏非本周课程」开关（对应外观设置 `showOtherWeeks`）。
  卡片底色与不透明表面色合成后再画，**网格辅助线不会从卡片里透出来**。
- 课程详情里有「共 x 周 · 已上 y 周 · 剩 z 周」进度。
- 翻周：标题 `‹ 第N周 ▾ ›` 单步 + 课表区 `HorizontalPager` 左右滑动翻周（一页一周）。
  看别的周时状态行右端出现小胶囊「回到本周」；状态行高度写死 30dp，切周时顶部不跳高度。
  改同步逻辑时注意：`pagerState.currentPage` 在拖动中就会变，用它驱动 `LaunchedEffect` 会打断手势，
  要用 `settledPage` 且拖动中不要主动 `animateScrollToPage`。
- 桌面小部件「今日课程」已实现并实测（RemoteViews 里**不能用 `<View>`**，色条得用 `TextView`）。
- 识别管线（DOM 兜底仍在）：接口抓包(`net`) → 课表格子(`box`) → 指定容器(`selector`) → 表格网格(`grid`) → 位置聚类(`geometry`)。
- 已实测：`assembleDebug` 通过；JVM 单测 20/20；js-harness 9/9；模拟器端到端（真实响应落库 → 第 6 周、第 14 周、小部件）。
- 排查真实教务页面的办法（会话还有效时）：取 `app_webview/Default/Cookies`，用宿主机直接调
  `TimeTableHandler.ashx` 拿 JSON 当基准。**Cookie 属敏感信息，用完删掉、不要入库。**
- 本轮未做（详见 `PROGRESS.md` §0.8 / §0.9）：多课表切换、`.ics`/分享口令导出、其它系统的「大节」兜底、更多小部件样式。
- 未决：本机到校园网 TCP 不通，「App 直连教务自动更新」没能本机复现；会话过期需用户手动登录一次。
  真实教务 `jw.example.edu.cn` 的登录实测仍需用户配合。
