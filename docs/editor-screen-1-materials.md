# 编辑器第 1 屏素材与实现说明

## 继续复用的素材

结构底图：

- `app/src/main/res/drawable-nodpi/bg_pencil_project_strip_full.png`
- `app/src/main/res/drawable-nodpi/bg_pencil_tools_rail_full.png`
- `app/src/main/res/drawable/bg_editor_preview_frame.xml`
- `app/src/main/res/drawable/bg_editor_workspace.xml`
- `app/src/main/res/drawable/bg_editor_issue_bar.xml`

图标素材：

- `app/src/main/res/drawable-nodpi/ic_pencil_editor_folder.png`
- `app/src/main/res/drawable-nodpi/ic_pencil_editor_grip.png`
- `app/src/main/res/drawable-nodpi/ic_pencil_editor_type_rect.png`
- `app/src/main/res/drawable/ic_editor_type_circle.xml`
- `app/src/main/res/drawable-nodpi/ic_pencil_editor_layers.png`
- `app/src/main/res/drawable-nodpi/ic_pencil_editor_pause.png`
- `app/src/main/res/drawable-nodpi/ic_pencil_editor_lock.png`
- `app/src/main/res/drawable-nodpi/ic_pencil_editor_fit.png`
- `app/src/main/res/drawable-nodpi/ic_pencil_editor_focus.png`
- `app/src/main/res/drawable-nodpi/ic_pencil_editor_widgets.png`
- `app/src/main/res/drawable-nodpi/ic_pencil_editor_chart.png`
- `app/src/main/res/drawable-nodpi/ic_pencil_editor_settings.png`

## 这轮的实现结论

首屏的主内容改为项目列表，所以：

- 不再使用 shape-first 的底部完成态卡片
- 底部只保留项目页签和当前容器列表
- 右侧属性面板继续保留，但只作为次级入口

## 演示文档层级

当前演示文档已经调整为：

- `project-hh301`
- `component-main-visual`
- `component-info-panel`
- `project-main-wallpaper`
- `component-atmosphere`

子项仍然沿用我们已有展示内容，不强行复刻用户预设人物。

## Android 实现要点

- `MainActivity` 负责当前容器导航
- `DemoWallpaperDocumentFactory` 负责真实层级数据
- `DocumentEditing` / `DemoWallpaperRuntime` 已支持把新增图形插入到指定组件
- 首页默认停留在项目层，不自动进图形参数

## 已废弃方向

下面这一套不再作为首页主结构：

- 图形对象胶囊
- 图形 / 涂装 / 图层 / 位置 / 动画 这组 shape-first 底部卡片
- 进入首页就默认选中某个方形并打开参数

如果后面还要做对象详情页，可以作为组件内部的第二层页面单独实现，但不能再拿来充当首页。
