# SpotChat

SpotChat 是一个面向 Android / Wear OS 手表的本地聊天软件原型，目标是在没有中心服务器的情况下，通过局域网或蓝牙直连通信，并在应用层做端到端加密。

## 当前能力

- Wear OS 优先的 Wear Compose 界面，按圆表和方表分别调整安全区、内容宽度和可见消息数量，中文 UI，并使用紧凑的聊天工具式首页、会话行、聊天工具栏和底部回复栏。
- 会话列表提供紧凑的横向筛选条，可按收藏、未读、置顶、星标、草稿、提及、未发送、锁定、静音、限时消息、回执状态和会话类型快速定位，并显示实时数量。
- 左滑或点击头像进入个人资料页，个人资料页使用 Wear `ScreenScaffold`、`ScalingLazyColumn` 和滚动指示器浏览身份、可信设备和头像；返回按钮、系统返回或 Wear 右滑返回聊天页，页面切换带滑入动画。
- 可修改显示名并从纵向头像列表中选择：第一个头像使用显示名首字母，其余头像为本地打包的图片资源。
- 个人资料页会展示本机短指纹、当前传输模式、信任状态，以及最近的可信设备，方便在手表上快速核对身份。
- 聊天页可点击头像或标题进入聊天信息页，查看会话类型、成员数量、消息数量和安全指纹。
- 非系统消息可点开消息操作页，预览原消息并使用自定义输入或快捷回复继续发送。
- 主界面保留快捷回复，并可通过 Wear OS 系统输入界面发送自定义消息。
- 本地身份密钥生成和持久化，不进入 Android 备份；私钥使用 Android Keystore 管理的 AES-GCM 密钥加密存储，避免把可直接使用的私钥明文写入文件系统。密钥是否由安全硬件承载取决于具体设备。
- P-256 ECDH 密钥协商、HKDF-SHA256 会话密钥派生、AES-GCM 消息加密。
- 配对指纹和短校验码，用于两台设备人工确认，降低中间人攻击风险。
- HELLO 只用于发现候选设备；必须完成基于会话密钥的挑战确认、证明对方持有对应身份私钥后，才会绑定或刷新可信传输路由。
- 已确认设备会保存为可信设备，后续重连可自动恢复可信状态，并保留本地联系人备注和首次信任时间。
- 加密消息带发送状态，收到消息后会回加密认证 ACK 并显示送达结果。
- 已收到的加密消息和 ACK ID 会写入本地 SQLite 唯一索引；应用重启后仍会拒绝已处理密文。
- 聊天历史、草稿、文本/语音离线发送队列和回执映射会在进程重启后恢复；状态以 Android Keystore 保护的 AES-GCM 认证加密快照写入应用私有的 `noBackupFilesDir/spotchat-chat-state/spotchat_state.bin`。
- 局域网发现每次启动默认关闭，用户点按“局域网”后才绑定发现/监听 socket；UDP beacon 只广播通用设备名，未认证 HELLO / 会话挑战不携带个人简介，未设置资料时也不会使用硬件厂商与型号作为默认显示名。
- 蓝牙 RFCOMM 只连接已配对设备；Android 12 及以上只请求 `BLUETOOTH_CONNECT`，不请求扫描权限。
- Wear 会话快照明确排除在云备份和设备迁移之外；聊天状态快照存放在 Android 天然不参与备份或设备迁移的 `noBackupFilesDir`，同时应用清单关闭备份；解密语音使用随机缓存文件，并在切换播放、失败、页面销毁及下次启动时清理。
- JVM 单元测试覆盖核心加密、协议编解码和上述安全配置。
- GitHub Actions 自动测试、lint、检查 release 构建，并打包使用临时 debug 签名的测试 APK，方便从 Actions artifacts 下载验证。

## 安全模型

SpotChat 不把聊天明文交给网络或蓝牙传输层。局域网发现不会在应用冷启动后自动运行，只有用户明确点按后才开始广播通用 beacon、监听连接并进入 HELLO/配对流程；认证前的公开握手只携带建立加密会话所需信息，不发送个人简介。两台设备交换公开身份密钥后，使用 ECDH 生成共享秘密，再派生 AES-GCM 会话密钥。协议 v2 在正式接受候选路由前执行加密挑战确认，并把双方 HELLO 元数据绑定到确认密文；单纯复制或篡改公开 HELLO 不能直接刷新可信联系人资料和路由。每条消息和送达回执都使用随机 nonce，并把协议版本、包类型、外层包 ID、发送者、接收者和发送时间放进 AAD，防止密文被跨上下文使用或把回执伪造成普通消息。

文本和语音在加密内容中携带稳定的逻辑消息 ID，而每个收件人、每次重试都使用新的外层包 ID。这样群聊引用保持一致，丢失 ACK 后可以安全重试，编辑后的重发也不会被误判为旧密文重放。ACK 必须同时匹配已发送的外层包 ID 和目标收件人。

文本、语音、回应和 ACK 的外层包 ID 会在 AEAD 验证通过后原子写入本地 replay 数据库。重复包不会再次进入界面；数据库按本机身份隔离、按时间清理并设有容量上限，同时排除在 Android 备份之外。

身份私钥使用 Android Keystore 管理的 AES-GCM 密钥加密后存储，避免把可直接使用的私钥明文写入应用文件。Android Keystore 可以阻止应用导出密钥材料，但是否使用安全硬件取决于设备、系统版本和密钥实现，SpotChat 不把硬件保护作为所有设备上的保证。详细安全设计见 [docs/IDENTITY_KEY_STORAGE.md](docs/IDENTITY_KEY_STORAGE.md)。

聊天历史、草稿、文本/语音 outbox 和回执映射会序列化为一个有版本的本地状态快照，再使用独立的 Android Keystore AES-GCM 密钥做认证加密。写入会先生成同目录临时文件，并在平台支持时使用原子替换，否则退回同目录覆盖替换，以降低进程在写入中途退出时留下不完整快照的风险；AES-GCM 标签同时验证机密性与完整性。加载时只有通过版本、身份绑定和认证校验的快照才会进入运行时，密文被篡改、截断或无法解密时会 fail closed，不会按明文或部分数据降级恢复。

该快照位于应用私有的 `noBackupFilesDir/spotchat-chat-state/spotchat_state.bin`。Android 的备份规则没有映射 `noBackupFilesDir` 的有效 domain，因为该目录本身就不在云备份和设备迁移数据集中；SpotChat 还在应用清单中设置 `android:allowBackup="false"`，并继续显式排除身份、可信设备、Wear 快照和 replay 数据库。

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

生成的 APK 位于 `app/build/outputs/apk/debug/`。debug 构建使用独立的应用 ID `com.weifurry.spotchat.debug` 和版本名后缀 `-debug`，因此可以与正式版并存。本地 debug 签名由 Android Gradle Plugin 使用当前开发电脑的默认 debug key 完成；仓库不保存任何签名私钥或密码。

正式发布必须使用私有 release key（或 Play App Signing）签名，并通过本地安全配置或 CI secrets 注入。不要把 release keystore、私钥或密码提交到仓库。

## 资源来源

- `app/src/main/res/drawable-nodpi/avatar_*.png` 由 DiceBear 的 Micah 头像风格通过 HTTP API 生成，并作为本地资源随 APK 打包。

## GitHub Actions APK

进入 GitHub 仓库的 **Actions** 页面，打开 `Android Build` 工作流，可以手动运行或在 `main` 分支 push 后自动运行。成功后下载名为 `SpotChat-test-only-ephemeral-debug-apk` 的 artifact，里面会有类似 `SpotChat-test-only-debug-8aaa563.apk` 的 debug APK。

这个 artifact 仅供测试。GitHub-hosted runner 会为构建生成临时 debug key，不同工作流运行的签名通常不同，因此后续下载的 APK **不能保证覆盖升级**；如果安装时提示签名不一致，请先卸载旧的 `com.weifurry.spotchat.debug`。它使用独立的 debug 应用 ID，不会覆盖正式版 `com.weifurry.spotchat`。

正式发布包不会作为这个 debug artifact 上传。release 构建应使用未提交到仓库的私有密钥签名，才能提供可信、可持续升级的分发渠道。

如果设备安装过早期 Actions 产物（它使用正式应用 ID `com.weifurry.spotchat` 和曾公开提交的固定 debug key），请先卸载再安装可信的正式版。旧 key 已进入 Git 历史，删除仓库中的文件无法撤销它，任何由该 key 签名的 APK 都不应再视为可信发布包。

## 当前限制

- 当前传输生命周期仍与前台界面绑定；应用退出、进入后台或进程被系统回收后不会持续监听新消息。
- 聊天状态持久化只用于同一安装、本机身份下的进程重启恢复；卸载、清除应用数据、Android Keystore 密钥丢失或更换本机身份后，旧快照不会恢复，也不会通过 Android 备份迁移到另一台设备。
- 中继协议与传输实现仍处于预留状态，因此当前界面只开放局域网和蓝牙模式。
- 当前会话密钥由长期身份密钥静态 ECDH 派生，尚未提供前向保密或消息密钥棘轮；正式安全发布前应迁移到经过审阅的临时握手/ratchet 协议。

## 后续方向

- 继续细化配对流程：独立附近设备列表、已信任设备管理、校验码大字确认页。
- 扩展后台传输生命周期、持久 outbox 调度和会话密钥轮换。
- 加入二维码或数字短码配对，减少手表输入负担。
- 实现中继传输通道，支持非同一局域网设备通过中继服务器转发加密消息。
