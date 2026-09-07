# Switch5G

小米 14（HyperOS 3 / Android 15）上定时切换 5G / 4G 的两个小工具：**开启5G** 与 **关闭5G**。
一个项目编译出两个独立 APK，配合「自动任务」定时启动，实现夜间自动降到 4G。

## 原理

Android 不允许第三方 App 直接调用 `TelephonyManager.setPreferredNetworkType()`（需要系统签名权限 `MODIFY_PHONE_STATE`，Android 9+ 隐藏 API 也全被屏蔽）。

唯一无需 root 的可行路径：

1. 电脑 adb 一次性授权 `WRITE_SECURE_SETTINGS`（重启不失效）
2. App 改写 `Settings.Global` 里的 `preferred_network_mode1/2` 字段
3. 高通平台的 telephony 模块监听到字段变化，自动重新选网

小米 14 是骁龙 8 Gen 3（高通平台），走的正是这条路径。

## 安装

1. 到 Actions 下载最新产物 `switch5g-apks`，里面有：
   - `switch5g-on5g-debug.apk` → 桌面图标「开启5G」
   - `switch5g-off5g-debug.apk` → 桌面图标「关闭5G」
2. 两个都装上（包名不同，可共存）

## 授权（只需一次）

手机开启开发者选项 + USB 调试，连电脑后执行：

```bash
adb shell pm grant com.dongp.switch5g.on android.permission.WRITE_SECURE_SETTINGS
adb shell pm grant com.dongp.switch5g.off android.permission.WRITE_SECURE_SETTINGS
```

## 校准（建议做一次）

不同机型/运营商的 `preferred_network_mode` 取值并不一致，所以让 App 自己学一遍最稳：

1. 桌面**长按**「开启5G」图标 → 点「校准设置」
2. 手机去 设置 → 双卡与移动网络 → 数据卡 → 网络类型选择 → 选「5G 优先」
3. 回到校准页 → 点【① 记录：当前 = 5G 开启状态】
4. 再切成「仅 4G」→ 点【② 记录：当前 = 5G 关闭状态】

校准值存在 `Settings.Global`，两个 App 共享，只需校准一次。
不校准也能用（走常见默认值 33 / 22），只是不一定准。

## 定时

二选一：

- **小米自动任务**：设置 → 更多设置 → 自动任务 → 新建「每天 23:00 → 启动应用『关闭5G』」，
  再建一条「每天 07:00 → 启动应用『开启5G』」。自动任务是系统应用，不受 Android 后台启动限制。
- **App 内置定时**：校准页里勾选「启用每日定时」并保存（走 AlarmManager + 广播，锁屏也能执行）

### 如果用内置定时，MIUI 上需要放行的两项

1. 设置 → 应用设置 → 应用管理 → 找到「开启5G / 关闭5G」→ **自启动**：打开
2. 同一页 → 省电策略 → 选**无限制**（否则夜里定时可能被省电策略推迟）


## 备用触发（广播）

```bash
adb shell am broadcast -a com.dongp.switch5g.ACTION_OFF -n com.dongp.switch5g.off/.SwitchReceiver
adb shell am broadcast -a com.dongp.switch5g.ACTION_ON  -n com.dongp.switch5g.on/.SwitchReceiver
```

## 排查

- 切换后没立刻生效 → 手动开关一次飞行模式
- 想看当前到底写的什么值 → 校准页点【复制诊断信息】
- 手动查看：
  ```bash
  adb shell settings list global | grep -i network_mode
  ```

## 编译

```bash
gradle assembleDebug   # 或 ./gradlew assembleDebug
```
产物：`app/build/outputs/apk/<flavor>/debug/`
