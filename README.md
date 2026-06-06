# SpotChat

SpotChat 是一个面向 Android / Wear OS 手表的本地聊天软件原型，目标是在没有中心服务器的情况下，通过局域网或蓝牙直连通信，并在应用层做端到端加密。

## 当前能力

- Wear OS 优先的 Wear Compose 界面，按圆表和方表分别调整安全区、内容宽度和可见消息数量，中文 UI，并使用紧凑的聊天工具式首页、会话行、聊天工具栏和底部回复栏。
- 左滑或点击头像进入个人资料页，个人资料页使用 Wear `ScreenScaffold`、`ScalingLazyColumn` 和滚动指示器浏览身份、可信设备和头像；返回按钮、系统返回或 Wear 右滑返回聊天页，页面切换带滑入动画。
- 可修改显示名并从纵向头像列表中选择：第一个头像使用显示名首字母，其余头像为本地打包的图片资源。
- 个人资料页会展示本机短指纹、当前传输模式、信任状态，以及最近的可信设备，方便在手表上快速核对身份。
- 聊天页可点击头像或标题进入聊天信息页，查看会话类型、成员数量、消息数量和安全指纹。
- 非系统消息可点开消息操作页，预览原消息并使用自定义输入或快捷回复继续发送。
- 主界面保留快捷回复，并可通过 Wear OS 系统输入界面发送自定义消息。
- 本地身份密钥生成和持久化，不进入 Android 备份。
- P-256 ECDH 密钥协商、HKDF-SHA256 会话密钥派生、AES-GCM 消息加密。
- 配对指纹和短校验码，用于两台设备人工确认，降低中间人攻击风险。
- 已确认设备会保存为可信设备，后续重连可自动恢复可信状态。
- 加密消息带发送状态，收到消息后会回加密认证 ACK 并显示送达结果。
- 已收到的加密消息 ID 会短期去重，重复密文包不会再次显示。
- 局域网 UDP 广播发现和 TCP 加密帧发送通道。
- 蓝牙 RFCOMM 加密帧发送/监听通道。
- JVM 单元测试覆盖核心加密和协议编解码。
- GitHub Actions 自动测试、lint、检查 release 构建，并打包固定 debug 签名 APK，方便直接从 Actions artifacts 下载。

## 安全模型

SpotChat 不把明文交给网络或蓝牙传输层。两台设备交换公开身份密钥后，使用 ECDH 生成共享秘密，再派生 AES-GCM 会话密钥。每条消息和送达回执都使用随机 nonce，并把包类型、消息 ID、发送者指纹等元数据放进 AAD，防止密文被跨上下文重放或把回执伪造成普通消息。

首次配对时仍然需要用户在两台设备上核对指纹或短校验码。没有这一步，任何点对点加密协议都无法可靠排除局域网或蓝牙附近的中间人。

## 本地构建

如果 Android SDK 在常见位置不可见，可在本机创建未跟踪的 `local.properties`：

```properties
sdk.dir=/path/to/Android/Sdk
```

然后运行：

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

生成的 APK 位于 `app/build/outputs/apk/debug/`。

## 资源来源

- `app/src/main/res/drawable-nodpi/avatar_*.png` 由 DiceBear 的 Lorelei Neutral 头像风格通过 HTTP API 生成，并作为本地资源随 APK 打包。

## GitHub Actions APK

进入 GitHub 仓库的 **Actions** 页面，打开 `Android Build` 工作流，可以手动运行或在 `main` 分支 push 后自动运行。成功后下载名为 `SpotChat-debug-fixed-signed-apk` 的 artifact，里面会有类似 `SpotChat-debug-fixed-8aaa563.apk` 的 debug APK。

这个 APK 使用仓库内固定 debug key：`app/signing/spotchat-debug.keystore`。它不是生产发布密钥，公开提交是为了让每次 Action 产物的 Android 签名保持一致。只要设备上已安装的是这套固定 debug key 签出来的 SpotChat，之后的新 Action APK 就可以直接覆盖安装。

## 后续方向

- 继续细化配对流程：独立附近设备列表、已信任设备管理、校验码大字确认页。
- 给身份私钥增加 Android Keystore 迁移路径，优先使用硬件支持的密钥保护。
- 增加消息重传、离线队列和会话密钥轮换。
- 加入二维码或数字短码配对，减少手表输入负担。
