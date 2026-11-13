# 页读（PageReader）

一款 Android 本地阅读器：把 **PDF 或一组图片** 当作小说/漫画来读，支持自定义章节、书签、进度记忆、夜间模式与亮度调节。

## 功能一览

- **导入**：从系统文件选择器导入 PDF，或一次多选导入多张图片（每张图片 = 一页）。
- **两种阅读模式**：左右翻页（`HorizontalPager`）与上下滚动（`LazyColumn`），随时切换。
- **自定义目录 / 章节**：
  - 一键读取 PDF 内置书签大纲自动建目录（基于 [PdfBox-Android](https://github.com/TomRoush/PdfBox-Android)）。
  - 手动新建章节：填写章节名 + 起始页 / 结束页（如第 1–30 页为第一章）。
  - 章节可改名、删除、点击跳转。
- **书签**：任意页加书签（可备注）、点击跳转、删除。
- **阅读进度记忆**：自动保存每本书读到哪一页，下次打开继续。
- **夜间模式 + 亮度调节**：暗色界面与页面暗化遮罩；亮度可调节或跟随系统。
- **书架**：多本书网格管理，长按删除。

## 技术栈与版本

| 项 | 版本 |
|---|---|
| Kotlin | 1.9.22 |
| AGP（Android Gradle Plugin） | 8.2.2 |
| Gradle | 8.4 |
| Compose BOM | 2024.02.01（Material3 1.2.0） |
| compileSdk / targetSdk | 34 |
| minSdk | 24（Android 7.0+） |
| JDK | 17 |
| Room | 2.6.1（KSP 1.9.22-1.0.17） |
| PdfBox-Android | 2.0.27.0 |

PDF 页面渲染使用系统内置 `android.graphics.pdf.PdfRenderer`，无额外渲染依赖；图片解码为自实现 `BitmapFactory` 降采样 + `LruCache`。

## 目录结构

```
PageReader/
├── settings.gradle.kts / build.gradle.kts / gradle.properties
├── gradle/wrapper/gradle-wrapper.properties
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/…
        └── java/com/pagereader/app/
            ├── ReaderApplication.kt
            ├── MainActivity.kt
            ├── data/       # Room 实体、DAO、数据库、Repository、设置
            ├── reader/     # PageSource(PDF/图片)、PDF 目录提取
            └── ui/         # 主题、书架、阅读器（翻页/滚动 + 目录/书签/设置）
```

## 编译运行

### 方式一：Android Studio（推荐）

1. 安装 **Android Studio Hedgehog（2023.1.1）或更高版本**，并安装 **JDK 17**。
2. `File → Open` 选择本项目根目录 `PageReader`，等待 Gradle 同步（首次会联网下载依赖）。
3. 连接真机或启动模拟器，点击 **Run ▶** 运行。

> 说明：源码中未随附 `gradle/wrapper/gradle-wrapper.jar`（二进制文件）。Android Studio 打开项目时会自动生成/下载对应 Gradle wrapper，无需手动处理。

### 方式二：命令行

本机需已安装 **JDK 17** 与 **Gradle 8.4**。首次执行前先本地生成 wrapper：

```bash
cd PageReader
gradle wrapper --gradle-version 8.4
./gradlew assembleDebug     # Windows 使用 gradlew.bat
```

产物在 `app/build/outputs/apk/debug/app-debug.apk`。

## 使用说明

1. 首页点击右下角 **+**，选择「导入 PDF」或「导入图片（可多选）」。
2. 点击书籍进入阅读；点击页面中间可显隐顶栏/底栏。
3. 顶栏右侧三个按钮：**目录**、**书签**、**设置**。
4. 目录里可「新建章节」或（PDF）「导入 PDF 目录」；点击章节名跳转。
5. 设置里切换翻页/滚动、开启夜间模式、调节亮度。

## 已知限制 / 后续可优化

- PDF 目录提取依赖 PDF 内嵌书签大纲，若 PDF 本身没有大纲则需手动建章。
- 夜间模式采用「暗色 UI + 页面暗化遮罩」，非真正的反色渲染；如需更护眼可自行叠加滤镜。
- 图片按屏幕宽度降采样解码，超大图的长宽比在滚动模式下首次加载会有轻微高度跳动。
- 应用图标为内置矢量图，可按需替换 `res/drawable/ic_launcher.xml`。
- 未开启混淆（`minifyEnabled = false`），便于调试。

## 常见问题

- **Gradle JDK 必须用 17**：本项目 AGP 8.2.2 + Gradle 8.4 需要 JDK 17。若 Android Studio 自带 JDK 是 25 等新版本，需在 `File → Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK` 里选 `Download JDK... → 17 (Temurin)`，下载后选中它。
- **缺少 SDK Platform 34 / Build-Tools 34.0.0**：在 SDK Manager 的 SDK Platforms 勾选 Android 14（API 34）、SDK Tools 勾选 Build-Tools 34.0.0（看不到具体版本时点右下角 `Show Package Details`）后 Apply。
- **依赖解析失败**：确认能访问 `google()` 与 `mavenCentral()`；若 `com.tom-roush:pdfbox-android:2.0.27.0` 拉取失败，可改回 `2.0.24.0`。
- **无法再读上次导入的文件**：本应用通过 SAF 持久化读权限保存 URI，若系统/文件管理器回收授权，需重新导入。
