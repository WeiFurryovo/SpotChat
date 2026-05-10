# SpotChat

SpotChat 是一个面向 Android / Wear OS 手表的本地聊天软件原型，目标是在没有中心服务器的情况下，通过局域网或蓝牙直连通信，并在应用层做端到端加密。

## 当前能力

- Wear OS 优先的圆形小屏 Compose 界面，中文 UI。
- 本地身份密钥生成和持久化，不进入 Android 备份。
- P-256 ECDH 密钥协商、HKDF-SHA256 会话密钥派生、AES-GCM 消息加密。
- 配对指纹和短校验码，用于两台设备人工确认，降低中间人攻击风险。
- 局域网 UDP 广播发现和 TCP 加密帧发送通道。
- 蓝牙 RFCOMM 加密帧发送/监听通道。
- JVM 单元测试覆盖核心加密和协议编解码。
- GitHub Actions 自动测试并打包 debug APK。

## 安全模型

SpotChat 不把明文交给网络或蓝牙传输层。两台设备交换公开身份密钥后，使用 ECDH 生成共享秘密，再派生 AES-GCM 会话密钥。每条消息使用随机 nonce，并把消息 ID、发送者指纹等元数据放进 AAD，防止密文被跨上下文重放。

首次配对时仍然需要用户在两台设备上核对指纹或短校验码。没有这一步，任何点对点加密协议都无法可靠排除局域网或蓝牙附近的中间人。

## 本地构建

如果 Android SDK 在常见位置不可见，可在本机创建未跟踪的 `local.properties`：

```properties
sdk.dir=/home/weifurry/Android/Sdk
```

然后运行：

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

生成的 APK 位于 `app/build/outputs/apk/debug/`。

## 后续方向

- 在 UI 上接入真实的配对流程：局域网发现列表、蓝牙已配对设备列表、校验码确认页。
- 给身份私钥增加 Android Keystore 迁移路径，优先使用硬件支持的密钥保护。
- 增加消息重传、收条、离线队列和会话密钥轮换。
- 加入二维码或数字短码配对，减少手表输入负担。
