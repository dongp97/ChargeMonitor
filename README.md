# 充电监测 (ChargeMonitor)

Android 原生充电监测工具：实时读取电池电压 / 电流 / 温度，计算功率并积分累计电量，保存充电会话曲线与历史记录。

## 功能
- 实时监测：功率 / 电压 / 电流 / 温度
- 曲线绘制：功率 / 电压 / 电流三线同屏，支持双指缩放与全屏切换
- 会话管理：充满不拔线不结束、短暂断开（<30s）合并、断开 ≥30s 新建会话
- 历史记录：会话列表 + 详情曲线回看
- 数据保留：采样明细 90 天，会话汇总永久

## 技术栈
- Kotlin + Android 原生，minSdk 26 / targetSdk 35
- Room + KSP，Kotlin Coroutines + Flow
- 自绘 Canvas 曲线，前台服务（dataSync）
- GitHub Actions 自动编译

## 构建
- Debug 签名先跑通，APK 体积目标 < 10MB
- 依赖版本统一由 `gradle/libs.versions.toml`（Version Catalog）管理，未来升级 Android 17 只需改该文件
