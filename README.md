# l2111pageloginverify

Paper 1.21.11 账号登录/注册验证插件，使用服务器下发的“验证书本”完成注册与登录。

## 功能特性
- 书本签名完成注册/登录（不留可编辑书本）
- 支持重置密码申请（管理员审批后强制重新注册）
- 管理员审核模式（Admin Verify），未批准玩家保持锁定
- 未验证玩家全行为阻断（移动/交互/聊天/指令/背包/伤害等）
- 背包满时自动暂存物品，验证后自动归还（保留 NBT/附魔/耐久）
- 支持隐藏未验证玩家
- 成功音效仅本人听到 + 可配置全服公告
- 书本内容/颜色/YAML 全部可配置
- 登录信息记录（IP / 时间 / 登录盐）
- 明文与加密数据分区存储（可切换）
- 安全限制：登录限速、输入长度限制、超时踢出
- 内置本地 Web 管理面板（纯 Java 生成，无外部 HTML）

## 命令
### 玩家可用
- `/dwgxverify`
  - 发放验证书本（自动判断登录/注册）
- `/dwgxverify login <account> <password>`
  - 直接登录（无需书本）
- `/dwgxverify reg <account> <password> [confirm]`
  - 直接注册（确认缺省时 = password）
- `/dwgxverify resetpass`
  - 打开重置密码申请页

### 管理员
- `/dwgxverify toggle`
  - 开/关 验证
- `/dwgxverify chat <on|off>`
  - 未验证玩家是否允许聊天
- `/dwgxverify sound <Sound> [volume] [pitch]`
  - 设置成功音效
- `/dwgxverify hide <on|off>`
  - 隐藏/显示未验证玩家
- `/dwgxverify encryption <true|false>`
  - 开/关 加密模式（HASHED/PLAINTEXT）
- `/dwgxverify adminverify <on|off>`
  - 开/关 管理员审核模式
- `/dwgxverify approve <player|uuid>`
  - 审核通过
- `/dwgxverify unapprove <player|uuid>`
  - 取消审核（锁定）
- `/dwgxverify reset <approve|reject> <player|uuid>`
  - 审核重置请求
- `/dwgxverify reloadconfig`
  - 重载配置并同步 Web

## 重置密码流程
1. 玩家在书本第 4 页填写 QQ / 正版昵称 / 同意条款并签名
2. 管理员在网页或指令审核
3. 通过后删除旧账号记录，玩家进入锁定状态并强制重新注册
   拒绝后下次进服提示拒绝原因（可配置）

## Web 管理面板
- 默认地址：`http://127.0.0.1:1337`
- 支持：查看用户、搜索分页、审核/反审核、重置审核、配置切换
- 支持 Basic Auth（`web.auth.username/password`）
- 可限制仅本机访问（`web.local-only`）

## 数据文件
- `users.yml`：`data.hashed` / `data.plain`
- `resets.yml`：重置请求记录
- `logs.yml`：login / pending / actions 事件滚动记录

## 关键配置（config.yml）
- `admin-verify-enabled`
- `encryption-enabled`
- `security.verify-timeout-seconds`
- `security.max-account-length / security.max-password-length`
- `security.pbkdf2-iterations`
- `title-fade-in-ms / title-stay-ms / title-fade-out-ms`
- `web.bind / web.port / web.local-only`
- `web.auth.*`
- `sound.success / sound.volume / sound.pitch`
- `logs.max-login / logs.max-pending / logs.max-actions`
- `book.*`（书本内容与颜色）

## 编译

### 前置条件
- JDK 21
- Gradle（本仓库当前仅保留 `gradle-wrapper.properties`，缺少 `gradle-wrapper.jar`）

### 使用本机 Gradle
```bash
gradle build
```

### 如果补全了 Wrapper（推荐）
Windows:
```bash
gradlew.bat build
```

Linux/macOS:
```bash
./gradlew build
```

构建产物默认输出到 `build/libs/`。

## 编码说明
- Java 源码必须 UTF-8 **无 BOM**
- `config.yml` 建议 UTF-8 **带 BOM** 以兼容 Windows 编辑器
