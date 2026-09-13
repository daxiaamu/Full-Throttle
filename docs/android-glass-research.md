# Android 液态玻璃实现调研

调研日期：2026-09-14。当前项目为 Java 原生 View，minSdk 26；测试设备一加 8T 当前 API 36。

## 优先验证：QmDeve/AndroidLiquidGlassView

- 仓库：https://github.com/QmDeve/AndroidLiquidGlassView
- 实现：https://github.com/QmDeve/AndroidLiquidGlassView/blob/master/core/src/main/java/com/qmdeve/liquidglass/impl/LiquidGlassimpl.java
- 着色器：https://github.com/QmDeve/AndroidLiquidGlassView/blob/master/core/src/main/res/raw/liquidglass_effect.agsl
- MIT 许可，复用代码必须保留版权与许可。
- Java 原生 View；API 33 起使用 RuntimeShader、RenderNode、RenderEffect。
- 将目标 View 记录到 RenderNode，通过相对窗口坐标定位取样；折射、色散放在 AGSL 中计算。
- 着色器用圆角距离场与梯度计算边缘偏移；中心与边缘采用不同采样处理。
- 参数不变时复用效果，模糊效果有缓存。不能仅照搬 README 的性能主张，仍需在本应用 GPU 高负载时测试。
- 上游说明 API 33 以下不渲染效果；本应用仍须保留旧系统兼容方案。
- 集成前需验证取样目标不包含玻璃层自身，避免递归；弹窗与主窗口坐标需单独验证。

## 对照：styropyr0/Prismal

- 仓库：https://github.com/styropyr0/Prismal
- 技术说明：https://github.com/styropyr0/Prismal/blob/master/TECHNICAL.md
- OpenGL ES 2.0、GLSurfaceView、背景位图上传纹理、分离高斯模糊、曲面折射与弹簧动画。
- 支持共享背景取样，避免每个控件重复捕获与模糊。
- 当前项目已有独立 GPU 耗电循环，引入更多 EGL 表面后的竞争、生命周期和层级成本需要实测。
- 因此优先学习其背景共享和几何处理，不立即整库接入。

## 当前修复与后续边界

1.0.17 先修复交互阻塞：CPU 材质与模糊移至单独低优先级线程；移除导致材质重建的按压缩放；服务状态及时回调。
此版本尚未接入上述开源库，也不能宣称已经实现它们的 GPU 管线。
下一步应先在独立控件验证 AGSL 方案，再替换现有材质，覆盖深浅色、旋转、弹窗、旧 API 回退以及满负载下菜单和停止操作。