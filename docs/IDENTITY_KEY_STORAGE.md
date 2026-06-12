# 身份密钥存储安全

## 实现概述

SpotChat 的本地身份密钥（P-256 ECDH 密钥对）现在使用 Android Keystore 加密存储。私钥不再以明文形式保存在 SharedPreferences 中，而是使用硬件支持的 AES-GCM 密钥加密后再持久化。

## 安全保护

### 当前保护（已实现）

1. **硬件支持的加密**：包装密钥存储在 AndroidKeyStore 中，在支持的设备上由 TEE（可信执行环境）或 StrongBox 保护。
2. **防提取**：AndroidKeyStore 密钥材料无法导出，攻击者即使获得 root 权限也无法直接读取包装密钥。
3. **AES-GCM 认证加密**：使用 256-bit AES-GCM 加密私钥，防止篡改和降级攻击。
4. **备份隔离**：通过 `backup_rules.xml` 和 `data_extraction_rules.xml` 排除身份密钥，防止通过 Android 备份泄露（无论明文或密文）。
5. **平滑迁移**：已有的明文私钥会在首次读取时自动迁移为加密存储，迁移完成后立即删除明文副本。

### 攻击面

- **调试或 root 访问仍可读取密文**：加密后的私钥仍保存在 SharedPreferences 中。虽然攻击者拿到密文后无法直接解密（需要 AndroidKeyStore 密钥），但如果攻击者可以运行代码注入（如在 root 设备上附加调试器），仍可能通过应用进程解密。
- **备份恢复到不同设备**：AndroidKeyStore 密钥绑定到设备硬件，无法跨设备迁移。如果用户通过其他方式（如手动文件拷贝）将 SharedPreferences 恢复到新设备，密文将无法解密，应用会生成新身份。

## 实现细节

### 密钥层次

```
AndroidKeyStore (TEE/StrongBox)
  └─ AES-GCM 256-bit 包装密钥（别名：spotchat_identity_wrapper_key）
       └─ P-256 私钥密文（存储在 SharedPreferences: spotchat_identity/private_key_encrypted）
```

### 存储格式

加密后的私钥以 `base64(iv):base64(ciphertext)` 格式保存：

```
SharedPreferences: spotchat_identity
├─ public_key: <base64(X.509 DER)>
├─ private_key_encrypted: <base64(12-byte IV)>:<base64(AES-GCM ciphertext + tag)>
└─ private_key: (legacy, 迁移后删除)
```

### 迁移流程

1. 应用启动时，`IdentityStore.getOrCreateIdentity()` 检查是否存在 `private_key_encrypted`。
2. 如果仅存在旧的 `private_key`（明文），则：
   - 解码明文私钥。
   - 使用 AndroidKeyStore 包装密钥加密。
   - 原子性写入 `private_key_encrypted` 并删除 `private_key`。
3. 后续读取直接使用加密路径。

## 代码位置

- 实现：[`app/src/main/java/com/weifurry/spotchat/crypto/IdentityStore.kt`](../app/src/main/java/com/weifurry/spotchat/crypto/IdentityStore.kt)
- 单元测试：[`app/src/test/java/com/weifurry/spotchat/crypto/IdentityStoreTest.kt`](../app/src/test/java/com/weifurry/spotchat/crypto/IdentityStoreTest.kt)
- 备份排除规则：
  - [`app/src/main/res/xml/backup_rules.xml`](../app/src/main/res/xml/backup_rules.xml)
  - [`app/src/main/res/xml/data_extraction_rules.xml`](../app/src/main/res/xml/data_extraction_rules.xml)

## 未来改进方向

### 直接 Keystore ECDH（需要 API 31+）

当前实现使用 AndroidKeyStore AES 密钥加密软件生成的 P-256 密钥（兼容 minSdk 26）。Android 12 (API 31) 支持直接在 Keystore 中生成和使用 ECDH 密钥，私钥材料永不离开硬件：

```kotlin
KeyGenParameterSpec.Builder(
    "spotchat_identity_ec",
    KeyProperties.PURPOSE_AGREE_KEY
)
    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
    .setUserAuthenticationRequired(false)
    .build()
```

这种方式下，ECDH 密钥协商通过 `KeyAgreement` 调用硬件完成，提取私钥的 API 会抛出异常。

### 用户认证绑定（可选）

对于高安全需求场景，可启用 `setUserAuthenticationRequired(true)`，要求用户在使用身份密钥前通过生物识别或 PIN 验证。这会增加操作复杂度（手表上的生物识别支持有限），但可防止设备解锁后的后台密钥滥用。

### 密钥轮换

当前身份密钥生成后永久使用。未来可实现定期轮换（如每年），旧身份密钥归档用于解密历史会话，新密钥用于新配对。
