# IO-Croft

Minecraft Fabric 模组（1.21.1）：把玩家数据（生命值、饥饿值、坐标、等级等）通过 **BLE / 串口 / TCP** 发送给外部硬件设备。

## 功能

- 红石驱动发送：对「MC 发送器」方块施加红石信号时，按自定义协议发送数据包
- 周期发送：按配置间隔自动发送玩家数据，可在设置页勾选要发送的内容
- 三种传输通道：BLE（SimpleJavaBLE）、串口（jSerialComm）、TCP + UDP 广播发现
- 按 `P` 键打开配置页：查看 TCP / 串口 / BLE 状态、扫描并连接 BLE 设备

## 构建

需要 JDK 21+：

```bash
./gradlew build
```

构建产物位于 `build/libs/`。

## 使用

1. 安装 Fabric Loader、Fabric API 与 Cloth Config（运行时依赖）
2. 将 `mcbledevice-1.0.0.jar` 放入 `mods/` 目录
3. 进入游戏后按 `P` 打开配置页，选择传输通道并连接设备

## 设备协议

- BLE 服务 UUID：`0xFFF0`
  - 写特征（主机 → 设备）：`0xFFF1`
  - 通知特征（设备 → 主机）：`0xFFF2`
- UDP 广播发现：发送 `MC_DISCOVER`，设备回复 `MC_DEVICE|IP|PORT` 后建立 TCP 数据通道
- 数据包格式：`module(1B) + command(1B) + length(1B) + data`

## License

本项目代码基于 CC0-1.0 发布。

> 注意：模组打包了 SimpleJavaBLE（BUSL-1.1 许可）。
