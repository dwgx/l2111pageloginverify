# l2111pageloginverify

Paper 1.21.11 账号登录/注册验证插件，使用服务器下发的“验证书本”完成注册与登录。

## 功能特性
- 通过书本签名完成注册/登录
- 支持“重置密码”请求（管理员审核后强制重新注册）
- 管理员审核模式（Admin Verify），未批准玩家无法进入正常状态
- 未验证玩家全行为阻断（移动/交互/聊天/指令/背包/伤害等）
- 背包满时自动暂存物品，验证后自动归还（保留 NBT/附魔/耐久）
- 支持隐藏未验证玩家
- 成功音效 + 公告消息
- 书本内容/颜色/YAML 全部可配置
- 登录信息记录（IP / 时间 / 登录盐）
- 明文与加密数据分区存储（可切换）
- 登录限速、输入长度限制、超时踢出
- 内置本地 Web 管理面板（纯 Java 生成，无外部 HTML）

## 命令
- `/dwgxverify`
  - 发放验证书本（自动判断登录/注册）
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
  - 取消审核
- `/dwgxverify reset <approve|reject> <player|uuid>`
  - 审核重置请求

## 重置密码流程
1. 玩家在书本第 4 页填写 QQ / 正版昵称 / 同意条款并签名
2. 管理员在网页或指令审核
3. 审核通过后：
   - 删除玩家旧账号记录
   - 玩家进入“锁定状态”（不会被踢），必须重新注册

## Web 管理面板
- 默认地址：`http://127.0.0.1:1337`
- 支持：查看用户、搜索分页、审核/反审核、重置审核、配置切换
- 支持 Basic Auth (`web.auth.username/password`)
- 可限制仅本机访问 (`web.local-only`)

## 数据文件
- `users.yml`
  - `data.hashed` / `data.plain` 分区存储
- `resets.yml`
  - 重置请求记录
- `logs.yml`
  - login / pending / actions 事件滚动记录

## 关键配置（config.yml）
- `admin-verify-enabled`: 是否启用管理员审核
- `encryption-enabled`: 加密模式
- `security.verify-timeout-seconds`: 超时踢出
- `title-fade-in-ms / title-stay-ms / title-fade-out-ms`: 标题显示时长
- `web.bind / web.port / web.local-only`
- `web.auth.*`
- `logs.max-login / logs.max-pending / logs.max-actions`
- `book.*`：书本内容与颜色

## 安全建议
- **不要把 Web 面板暴露公网**（除非开启强密码 + 访问限制）
- 加密模式仅保护密码存储，Web 面板仍可展示敏感信息，请谨慎使用

## 编译
```bash
./gradlew build
```

## 编码说明
- Java 源码必须 UTF-8 **无 BOM**（javac 要求）
- `config.yml` 建议 UTF-8 **带 BOM** 以兼容 Windows 编辑器
