# ClassSchedule 课表

Android 课表 App（Kotlin + Jetpack Compose），外观与交互参考 WakeUp课程表。

## 功能

- **教务导入**：内置浏览器登录后自动识别课表（接口抓包 → 课表格子 → 指定容器 → 表格网格 → 位置聚类），
  一次拿到全学期课程、精确节次、单双周与开学日期
- **图片识别**：选课表截图离线识别（ML Kit 中文 OCR，实验功能）
- **课程**：手动增删改、多时间段、单双周、冲突平分显示、已上/剩余周数进度
- **课表**：`‹ 第N周 ›` 与左右滑动翻周、隐藏非本周课程、约 20 项外观自定义、深色模式
- **桌面小部件**：今日课程（跟随深色模式）
- **导入导出**：CSV、JSON 备份

## 构建

```bash
./gradlew :app:assembleDebug      # 调试包
./gradlew :app:testDebugUnitTest  # 单测（25 个）
./gradlew :app:assembleRelease    # 发布包（R8 + 资源压缩）
```

要求 JDK 17、Android SDK 36；`release.jks` 与 `local.properties` 不入库。

## 开源

[MIT](LICENSE)
