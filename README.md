# Yue Weather（Compose 新版）

一款开源的 Android 天气应用，支持全球任意地点的天气预报与降雨雷达。本版本在原
[RadarWeather / Privacy Friendly Weather](https://github.com/SecUSo/privacy-friendly-weather)
的基础上完成了 **UI 层的 Jetpack Compose 重构**，数据源为
[Open-Meteo](https://open-meteo.com/)（非商业用途免费），雨量雷达由
[RainViewer API](https://www.rainviewer.com/api.html) 提供。

## 新版亮点

- **Jetpack Compose 全面重写天气详情页**：概览卡片、逐小时预报、一周预报、周温度曲线图全部迁移到声明式 UI（Material 3）
- **自定义 Canvas 图表**：使用 Compose Canvas 重绘周温度折线图 + 降水柱状图 + 冰点虚线 + 双 Y 轴，移除第三方图表库 williamchart
- **Material 3 下拉刷新**：原生 `PullToRefreshContainer` 指示器，兼容 Android 12+ stretch overscroll
- **MVVM 架构**：`WeatherDataViewModel` 以 Kotlin `StateFlow` 作为唯一数据源，数据库读取运行在 IO 协程上，带时序保护，杜绝旧数据覆盖新数据的竞态
- **性能修复**：消除列表滚动时主线程同步 SQLite 查询、组合期间重复计算等 ANR 隐患
- **稳定性修复**：修复 LazyList 重复 key 崩溃、状态互相覆盖导致的周视图选中错乱、编码问题引起的符号乱码等

## 功能

- 全球任意城市当前天气、逐小时（含 15 分钟级降水）、一周预报
- 交互式降雨雷达（osmdroid + Leaflet 瓦片）
- 点击周视图某一天可自动滚动到对应小时的预报位置
- 日出日落、紫外线指数、气压、湿度等可选显示项（设置中开关）
- 多种桌面小部件（4 种样式）+ 雷达小部件，支持 GPS 自动定位更新
- 中/英/波兰语/葡萄牙语多语言支持

## 技术栈与构建要求

| 项目 | 版本 |
|---|---|
| Java / Kotlin JVM Target | 17 |
| Gradle | 8.9 |
| Android Gradle Plugin | 8.7.2 |
| Kotlin | 1.9.22 |
| Jetpack Compose BOM | 2024.04.01（Material 3 1.2.1）|
| minSdk / targetSdk | 21 / 35 |

```bash
git clone <本仓库>
./gradlew :app:assembleDebug     # 构建 APK
./gradlew :app:testDebugUnitTest # 运行单元测试
```

需要 JDK 17 及以上环境。

## 项目结构

```
app/src/main/java/org/zhangjq0908/weather/
├── activities/        # Activity：主页、管理城市、设置、关于、雨量雷达
├── ui/
│   ├── compose/       # Compose UI（新版）
│   │   ├── WeatherCityComposeFragment.kt   # Compose 宿主 Fragment + 下拉刷新
│   │   ├── WeatherDataViewModel.kt         # StateFlow 数据源（MVVM）
│   │   ├── WeatherForecastScreen.kt        # 详情页骨架（LazyColumn）
│   │   ├── OverviewCard.kt                 # 当前天气概览卡片
│   │   ├── ForecastComposables.kt          # 小时/周列表条目
│   │   └── WeeklyChart.kt                  # Canvas 温度/降水图表
│   ├── viewPager/     # ViewPager2 适配器（每城市一页）
│   └── updater/       # 后台数据推送广播（ViewUpdater）
├── database/          # SQLiteHelper 及实体类
├── services/          # WorkManager 定时更新任务
├── util/              # 纯函数工具（时间/昼夜判断，已覆盖单元测试）
└── widget/            # 桌面小部件
app/src/test/           # JUnit 单元测试
```

## 数据来源与致谢

- 天气数据：[Open-Meteo](https://open-meteo.com/)（CC BY 4.0）
- 降雨雷达：[RainViewer API](https://www.rainviewer.com/api.html)
- 地图：[OpenStreetMap](https://www.openstreetmap.org/copyright)（ODbL）/ [osmdroid](https://github.com/osmdroid/osmdroid) / [Leaflet](https://github.com/Leaflet/Leaflet)（2-clause BSD）
- 图标：[Google Material Design Icons](https://material.io/resources/icons/)（Apache 2.0）
- 网络请求：[Volley](https://github.com/google/volley)（Apache 2.0）
- 本应用派生自 SECUSO 的 [Privacy Friendly Weather](https://github.com/SecUSo/privacy-friendly-weather)

## 许可证

GPLv3 © 原作者 woheller69 及贡献者。详见仓库内 LICENSE 文件。

## 反馈

发现 Bug 请在 GitHub 提交 Issue，请注明复现步骤、Android 版本与设备型号；欢迎提交 Pull Request。
