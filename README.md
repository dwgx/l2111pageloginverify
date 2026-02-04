# l2111pageloginverify

一个基于 Paper 1.21.11 的登录/注册验证插件，使用“服务器发放的验证书本”完成账号验证。

## 功能特性
- 通过书本签名完成注册/登录
- 未验证时完全阻止操作（移动/交互/聊天/指令/背包/伤害等）
- 背包满时自动暂存被顶掉物品，验证后自动归还（保留 NBT/附魔/耐久）
- 可隐藏未验证玩家
- 成功音效 + ActionBar 提示
- 书本内容/颜色完全可在 YAML 中配置
- 记录登录信息（IP / 时间 / 每次登录盐）
- 支持加密/明文两套数据切换（互不覆盖）

## 指令
- `/dwgxverify`
  - 给玩家发放验证书本（根据是否已注册决定登录/注册）
- `/dwgxverify toggle`
  - 开关验证功能
- `/dwgxverify chat <on|off>`
  - 开关未验证聊天
- `/dwgxverify sound <Sound> [volume] [pitch]`
  - 设置成功音效
- `/dwgxverify hide <on|off>`
  - 设置未验证玩家是否隐藏
- `/dwgxverify encryption <true|false>`
  - 切换加密模式（true=HASHED / false=PLAINTEXT）
  - 两套数据独立保存，切换不会互删

## 配置
详见 `config.yml`，常用关键项：
- `encryption-enabled`: true/false
- `log-pending`: 记录暂存/归还日志
- `log-login-info`: 记录登录 IP/盐
- `book.*`: 书本内容与颜色

## 数据结构（users.yml）
数据按模式分区：
- `data.hashed`：加密模式数据
- `data.plain`：明文模式数据

示例：
```
data:
  hashed:
    <uuid>:
      account: ...
      password: ...
      salt: ...
      mode: HASHED
      last_login:
        ip: "1.2.3.4"
        time: 1730000000000
        salt: "..."
      pending:
        bytes: "..."
        slot: 8
      pending_log:
        stored:
          time: 1730000000000
          slot: 8
          type: DIAMOND_SWORD
          amount: 1
        restored:
          time: 1730000001234
          slot: 8
          type: DIAMOND_SWORD
          amount: 1
```

## 编码说明
- Java 源码必须 UTF-8 **无 BOM**（javac 限制）
- `config.yml` 采用 UTF-8 **带 BOM**，方便 Windows 编辑器

## English
See `README_EN.md`.


## Web ??
- ???????? `1337`??? `config.yml` ?? `web.bind` / `web.port`
- ????????????????/??/??/?????
- ?? Basic Auth?`web.auth.username` / `web.auth.password`

