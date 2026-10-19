# ClassSchedule 课表

Android 课表应用 — Kotlin + Jetpack Compose 开发，外观与交互参考 WakeUp 课程表。
支持教务导入、课表截图识别、桌面小部件、CSV / JSON 备份与丰富的显示项自定义。

![version](https://img.shields.io/badge/version-0.6.0-blue)
![minSdk](https://img.shields.io/badge/minSdk-26-green)
![license](https://img.shields.io/badge/license-MIT-lightgrey)
![platform](https://img.shields.io/badge/Android-36-3DDC84)

## 功能

- **教务导入**：内置浏览器登录后自动识别课表（抓包 → 指定容器 → 表格网格 → 位置聚类），
  一次拿到全学期课程、精确节次、单双周与开学日期
- **图片识别**：选课表截图离线识别（ML Kit 中文 OCR，实验功能）
- **课程管理**：手动增删改、多时间段、单双周、冲突平分显示、已上 / 剩余周数进度
- **备注**：课程详情里直接编辑备注；支持「仅本周」和「所有周」两套独立备注，可同时存在
- **课表**：`‹ 第N周 ›` 与左右滑动翻周、隐藏非本周课程、约 20 项外观自定义、深色模式
- **外观实时预览**：调整课表外观时整页淡出 70%，背后显示真实课表，边调边看效果
- **桌面小部件**：今日课程（跟随深色模式）
- **导出**：CSV、JSON 备份与恢复，课表长图分享 / 保存到相册
- **日志**：本地文件日志（不联网），不记录课程名 / 老师 / 地点，网址会做脱敏

## 截图

> 截图整理中。当前版本效果可在 [Releases](https://github.com/AlphaEren/ClassSchedule/releases) 页面的安装包中查看。

## 快速开始

要求 **JDK 17**、**Android SDK 36**，Gradle 用仓库自带的 `gradlew` 即可。

```bash
git clone https://github.com/AlphaEren/ClassSchedule.git
cd ClassSchedule

# 本地 SDK 路径（也可以用全局 SDK 配置）
echo "sdk.dir=/path/to/Android/Sdk" > local.properties

# 签名（发布包才需要；不要提交到仓库）
# release.jks 放项目根目录，签名配置从 local.properties 读取
```

```bash
./gradlew :app:assembleDebug      # 调试包
./gradlew :app:testDebugUnitTest  # 单元测试（46 个）
./gradlew :app:assembleRelease    # 发布包（R8 + 资源压缩）
```

`release.jks`、`local.properties`、`PROGRESS.md`、`snapshots/` 已在 `.gitignore` 中，不会入库。

## 项目结构

```
app/src/main/java/com/lorat/classschedule/
├── data/            # 数据层：模型、仓库、导入解析、OCR、CSV、日志
│   ├── TimetableExtractor.kt   # 网页 / JSON → 课表模型（核心识别规则）
│   ├── OcrTimetable.kt         # 截图 OCR → 课表
│   ├── ScheduleRepository.kt   # 课表读写（JSON 持久化）
│   └── TimetableLayout.kt      # 格子排布（冲突平分、跨节）
├── ui/
│   ├── NetSniffer.kt           # 内置浏览器抓包（installJs）
│   ├── TimetableImage.kt       # 课表长图渲染 / 保存相册
│   ├── timetable/              # 课表主页、格子、日期栏
│   ├── course/                 # 课程编辑、详情面板、课程列表
│   ├── imports/                # 截图识别
│   └── webimport/              # 教务导入流程
├── widget/          # 桌面小部件（RemoteViews）
├── i18n/            # 中文文案 → English 映射（Lang.tr / EnStrings）
└── theme/           # 主题、深色模式

tools/js-harness/    # 桌面上跑识别 JS 用例（无头 Chrome，不用模拟器）
docs/                # 设计 / 参考文档
```

## 教务导入（实现原理）

1. 内置浏览器（WebView）加载教务页面，用户自己登录；
2. 注入抓包脚本，收集 XHR / 页面 HTML；
3. `TimetableExtractor.kt` 解析：
   - 表格结构 → 找课表容器 → 提取单元格文本（课程名 / 教师 / 周次 / 教室）→ 位置聚类定节次；
   - XHR 返回 JSON → 按字段别名（课程名、星期、节次、周次位图、开始日期）解析；
4. 结果写入本地 JSON，之后每次打开静默更新。

**导入失败时**：先用 `tools/js-harness` 在电脑上复现（见下），确认是「没抓到数据」还是「抓到但解析规则不匹配」；
提 issue 时附**脱敏后**的 JSON / 截图，不要带真实学号、姓名、校名。

## 图片识别（OCR）

- 基于 ML Kit 中文 OCR，离线识别课表截图；
- 目前是实验功能，识别率受截图分辨率、表格边框、文字排版影响；
- 入口：导入 → 图片识别。

## 识别回归（tools/js-harness）

把 `TimetableExtractor.kt` 的 `RAW_SCRAPER_JS` 和 `NetSniffer.kt` 的 `installJs` 抽出来，
注入 `cases/*.html`，用无头 Chrome 跑一遍并打印识别结果。

```powershell
cd tools/js-harness
.\cases.ps1                                              # 生成测试页面（改用例时才需要）
powershell -ExecutionPolicy Bypass -File .\run.ps1       # 全部用例
powershell -ExecutionPolicy Bypass -File .\run.ps1 -filter "b_*"  # 只跑匹配用例
```

10 个用例覆盖：标准表格（a_kbtable）、卡片网格（b_boxes）、jwglxt（c_jwglxt）、非课表（d_none）、
竖排节次（e_stacked）、无表头（f_nodays）、一格多课（g_multi）、XHR 数据（h_xhr）、
真实教务 SaaS（i_exesac，已脱敏）、kbpro SPA（j_kbpro）。详细期望见 `tools/js-harness/README.md`。

## 隐私

- 教务导入在本机完成，不上传账号密码；
- 本地日志只写文件、不联网，不记录课程名 / 老师 / 地点；
- 收集问题用例时请脱敏（校名、姓名、学号、教室号）；
- `release.jks` 和任何密钥文件都不入库，CI 里用 Secret 注入。

## 构建与发布

- 版本号规则：语义化 `MAJOR.MINOR.PATCH`，1.0 之前都是 `0.x.y`；`versionCode` 每次发布 +1；
- 发布流程：单测 → release 装机自测 → 改 `app/build.gradle.kts` 的版本号 →
  commit → `git tag vX.Y.Z` → push → `gh release create` 附 `app-release.apk`；
- release 包只含 `arm64-v8a` + `x86_64`；R8 + 资源压缩已开启（keep 规则见 `app/proguard-rules.pro`）。

## 贡献

- `feature/*` 新功能、`fix/*` 修复、`release/*` 发布准备；
- 提交信息用中文、写清楚改了什么；
- 新增教务系统适配时，请同时补 `tools/js-harness` 用例和脱敏样例；
- PR 前跑一遍 `:app:testDebugUnitTest`。

## 常见问题

- **导入后课表全堆在一起**：多半是节次字段没识别到，检查该教务系统的节次字段名；
- **周次偏移**：部分系统周次位图第 0 位是「开学前那周」，需要忽略；
- **跨节识别成单节**：检查该学校的节次时间表（`ClassTimePatterns`）是否匹配；
- **日志在哪**：`/sdcard/Android/data/com.lorat.classschedule/files/logs/`，按天保存最近 7 天。

## 许可证

[MIT](LICENSE) © 2026 AlphaEren

感谢 WakeUp 课程表在 UI / 交互上的启发；OCR 基于 Google ML Kit 中文识别。
