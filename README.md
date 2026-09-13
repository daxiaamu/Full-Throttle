# 全速耗电 / Full Throttle

原生 Android 应用，Android 8.0 及以上，无网络权限，无第三方运行时依赖。

## 功能

- 主界面大开关，一键开始/停止。
- 每个可用 CPU 逻辑处理器一个计算线程；独立 EGL 离屏 GPU 着色运算，不依赖 Activity 或屏幕刷新率。
- 前台服务、常驻通知和通知停止按钮；PARTIAL_WAKE_LOCK 保持后台运算。
- 应用显示在前台时屏幕最大亮度、常亮；停止后恢复窗口原有系统亮度策略。后台不修改其他应用的亮度，也不强制点亮锁屏。
- 显示实时电量、电池温度、实时功耗（W）。W = |电流 μA| × 电压 mV / 10^9，充电时显示电池净充电功率，不代表整机输入功率。设备未提供实时电流时显示不可用。
- 根据本次运行的实测电荷下降速度预测距离目标电量的时长、日期和时间；至少采样 60 秒。无电荷计数器时使用至少两个百分点的下降速度，可能需要更久。插电时暂停预测，拔电后重新采样。
- 停止电量 1–100%，默认 20%，设置持久化且立即生效。达到阈值、电池温度达到 50°C 或无法读取电量时停止。
- 不在开机或进程被系统终止后自动重新开始耗电。

## 构建

需要 JDK 17+、Android SDK 37（本地平台目录可能是 android-37.0）、Gradle 9.6.1。
设置 ANDROID_HOME，或使用 local.properties 配置 sdk.dir。

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

APK：app/build/outputs/apk/debug/app-debug.apk。

## 测试

单元测试覆盖电流/电压单位转换、无效传感器读数、采样预热、目标电量预测、百分比降级、插电重置和目标阈值。
实机验收：允许通知 → 开启 → 观察功率/电量 → 切后台验证通知与负载 → 通知停止；将目标电量提高至当前电量验证立即停止；检查重新进入界面及屏幕亮度恢复。

## 系统限制

普通应用无法强制所有核心固定最高频率、阻止热降频，或保证厂商系统永不清理后台。此应用持续提交 CPU/GPU 运算；实际功耗由硬件、温度和系统调度决定。GPU 初始化失败时明确显示状态，CPU 仍可运行。

API 依据：
- https://developer.android.com/develop/background-work/services/fgs/service-types
- https://developer.android.com/reference/android/os/BatteryManager
## 本机验证记录

2026-09-14：assembleDebug 成功；7 项 JUnit 测试通过；lintDebug 通过（0 错误，有国际化、备份配置和有意持有前台服务唤醒锁的提示）。尚未进行手机安装、GPU 实际功耗、长时间后台与温升实测。

本机 Windows 中文用户目录下，Gradle 测试 JVM 的参数文件需要与系统编码匹配。使用 JDK 17，验证命令：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug '-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=GBK' --offline
```

源码编译显式使用 UTF-8。其他机器可使用上面的普通构建命令；首次构建不要加 --offline。
