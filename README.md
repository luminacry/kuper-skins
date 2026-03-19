# KLWP Editor Replica Demo

一个基于 Android 原生 `WallpaperService + Canvas` 的 KLWP 编辑器复刻实验项目。

当前版本重点不在完整功能生态，而在两件事：

- 复刻 KLWP 编辑器页的 UI 结构、比例和交互层次
- 打通“编辑器预览 -> 运行时文档 -> 动态壁纸渲染”的最小闭环

## 当前包含内容

- `MainActivity`
  - 中文化编辑器界面
  - 顶部菜单栏、中央预览区、右侧工具塔、底部图层列表、右侧属性面板
  - 图层选中、显隐、删除、填充色修改、位移/透明度/缩放调整
- `EditorRuntimePreviewView`
  - 预览区实时渲染
  - 图层点击选中
  - 形状拖拽与缩放
  - 文档快照回传给编辑器 UI
- `DemoWallpaperRuntime`
  - 编辑模式与壁纸模式共用运行时
  - 文档仓库、撤销/重做、形状新增与删除
- `DemoWallpaperService`
  - 动态壁纸绘制入口
  - 页面偏移、触摸涟漪、电量采样

## 构建环境

本项目本地验证环境如下：

- Android Gradle Plugin `9.0.1`
- Gradle `9.2.1`
- Java `17+`
- Android SDK platform `36.1`
- Build tools `36.1.0`

如果你的环境变量已经正确配置，可直接执行：

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
```

如果需要临时指定本机路径，可以按下面这样设置：

```powershell
$env:JAVA_HOME='C:\Users\HUA\.jdks\openjdk-25.0.2'
$env:ANDROID_HOME='C:\Users\HUA\AppData\Local\Android\Sdk'
$env:ANDROID_SDK_ROOT='C:\Users\HUA\AppData\Local\Android\Sdk'
.\gradlew.bat assembleRelease
```

## 安装包输出

调试包输出：

```text
app/build/outputs/apk/debug/app-debug.apk
```

正式版包输出：

```text
app/build/outputs/apk/release/app-release.apk
```

## 关于 release 签名

当前仓库没有独立发布 keystore。

为了先产出“可安装”的 release 安装包，当前 `release` 构建临时复用了本机默认 debug keystore。
这适合真机安装、自测和视觉对比，不适合直接作为应用市场正式发布包。

如果后续要上架或长期分发，需要再补正式 release keystore，并替换当前签名配置。

## 快速体验

1. 安装 `app-debug.apk` 或 `app-release.apk`
2. 打开应用进入编辑器首页
3. 在中间预览区点击图层
4. 通过底部列表和右侧属性面板调整图层
5. 如需测试壁纸运行态，再进入系统壁纸预览流程
