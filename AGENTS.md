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

Start-Process "C:\Users\Lorat\Android\Sdk\emulator\emulator.exe" -ArgumentList '-avd','classschedule'   # 需要可视化时
$adb="C:\Users\Lorat\Android\Sdk\platform-tools\adb.exe"
& $adb wait-for-device
& $adb install -r app\build\outputs\apk\debug\app-debug.apk
& $adb shell am start -n com.lorat.classschedule/.MainActivity

# 截图：必须走 sdcard，PowerShell 的 `>` 重定向会破坏二进制
& $adb shell screencap -p /sdcard/s.png; & $adb pull /sdcard/s.png .\s.png; & $adb shell rm /sdcard/s.png
```

识别逻辑免模拟器回归：`tools/js-harness/README.md`（8 个用例）。

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

- 识别管线：接口抓包(`net`) → 课表格子(`box`) → 指定容器(`selector`) → 表格网格(`grid`) → 位置聚类(`geometry`)，外加「自动点查询按钮重试」。
- 已完成：学校记忆、静默自动更新、连堂课合并为一张跨行卡片渲染。
- 已实测：`assembleDebug` 通过；js-harness 8/8；模拟器端到端（卡片式 12 门 / 正方 12 门 / 接口型 7 门）。
- 最大未决项：真实教务 `jw.example.edu.cn`（ASP.NET + iframe）无法登录实测；识别不出时向用户索取「详情」弹窗里的 `表格/网格` 整行。
