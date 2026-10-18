# js-harness

在电脑上跑「课表识别 JS」，不用开模拟器。

`TimetableExtractor.kt` 里的 `RAW_SCRAPER_JS` 和 `NetSniffer.kt` 里的 `installJs`
会被原样抽出来，注入到 `cases/*.html` 里，用无头 Chrome 跑一遍并打印识别结果。

## 用法

```powershell
cd C:\Users\Lorat\ClassSchedule\tools\js-harness
.\cases.ps1                      # 生成 8 个测试页面（改了用例才需要跑）
powershell -ExecutionPolicy Bypass -File .\run.ps1            # 全部
powershell -ExecutionPolicy Bypass -File .\run.ps1 -filter "b_*"   # 只跑一个
```

参数：`-Root <项目根>`、`-Chrome <浏览器 exe>`（默认自动找 Chrome/Edge）。

## 用例与期望

| 文件 | 场景 | 期望 |
|---|---|---|
| `a_kbtable` | 正方/URP `#kbtable`，td 内 `名称<br>教师<br>周次<br>教室` | box，12 门，节次 1..10 |
| `b_boxes` | 卡片式课表（示例截图那种 div 网格，含跨两行、空周次行） | box，12 门，`高等数学A` = 第4-5节 |
| `c_jwglxt` | jwglxt 的 `kbcontent1..4` 结构 | box，3 门，节次 1/3/5（**不能**变成 1-2/3-4） |
| `d_none` | 根本不是课表 | ok=false，error=no-grid |
| `e_stacked` | 节次竖排在一个格子里 `①<br>②…` | box，4 门，含 1-2 / 3-4 |
| `f_nodays` | 没有星期表头 | box，按列自动编号 d1..d5 |
| `g_multi` | 一个格子两门课 + rowspan 跨行 | box，拆成 3 门 |
| `h_xhr` | DOM 里没有课表，课程来自 XHR 返回的 JSON | mode=**net**，6 门，认 `jcs:"0102"` / `jcs:[7,8]` / `zcd:"1-16"` |
| `i_exesac` | **真实**教务处 SaaS 页面（示例大学）：DOM 只有一个空 table，数据来自 `TimeTableHandler.ashx` | mode=net，**52 门**，节次跨两节（1-2/3-4/5-6/9-10），含单周 1-13、双周 2-12、多段周次；`sections=10`、`sourceCurrentWeek=6`、`semesterStart=2026-09-07` |
| `j_kbpro` | kbpro 课表 SPA：`getSchedulejson` 的 `weekDescription` 周次位图 + `scheduleInfo` 的开学日期/当前周 | mode=net，4 门；周次按位图（第 0 位是开学前那周，**不能**整体偏移一周）；`sections=11`（08:30-09:10 … 18:45-19:25）、`sourceCurrentWeek=8`、`semesterStart=2026-08-24`、`lastTeachingWeek=24` |

> `i_exesac` 里的 JSON 是从教务接口抓下来后**脱敏**（校名、教师姓名、楼名、教室号全部替换成占位，
> 课程名与结构保持原样），所以它同时是「这套系统长什么样」的档案：`ClassTimePatterns` 用 5 分钟为单位的
> 节号表示上下课时间，`WeekInterval>0` 表示隔周（奇偶跟着 `WeekStart` 走），星期是 7 个布尔字段。

## 注意

- 用例页依赖 `file://`，脚本里已加 `--allow-file-access-from-files`。
- `h_xhr` 用 GET 读 `blob:` URL；Chrome 不允许对 `blob:` 发 POST，想看 POST 抓包请用真实 HTTP 服务（模拟器回归里有）。
- 无头 Chrome 用 `--headless`（旧版）。若结果全空，先 `Stop-Process chrome` 杀掉残留进程再跑。