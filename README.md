# l2111pageloginverify

**Paper 1.21 登录验证插件 — 用"验证书本"完成注册、登录与密码重置**
**Paper 1.21 login verification plugin — register, login, and reset passwords via a server-issued verification book**

---

## Overview

`l2111pageloginverify` is a login/verification plugin for Paper (Minecraft) servers. Players who join are locked until they complete registration or login by filling in and signing a server-issued verification book. It ships with a built-in local web admin panel and an optional admin-approval flow, built for small/private servers that want their own account gate.

## 概述

`l2111pageloginverify` 是一个 Paper (Minecraft) 服务端登录验证插件。玩家进服后被锁定，必须通过服务器发放的"验证书本"（在书页中填写账号/密码并签名）完成注册或登录，之后才能移动、交互、聊天。插件内置本地 Web 管理面板与管理员审核流程，适合需要自建账号体系的小型/私人服务器。

---

## Features

- Register/login by signing the verification book (no editable book left behind)
- Password reset requests with admin approval (forces re-register)
- Admin verification mode — unapproved players stay locked
- Full action block before verification (movement, interaction, chat, commands, inventory, damage)
- Safe-store items when inventory is full, auto-restore after verification (keeps NBT/enchant/durability)
- Option to hide unverified players
- Success sound local-only + configurable broadcast message
- Book content/colors fully configurable via YAML
- Login info tracking (IP / time / per-login salt)
- Separate datasets for encrypted (PBKDF2) vs plaintext mode, switchable at runtime
- Security limits: login rate limiting, input length caps, verify-timeout kick
- Built-in local web admin panel (pure Java, no external HTML files)

## 功能

- 书本签名完成注册/登录（不留可编辑书本）
- 密码重置申请，管理员审批后强制重新注册
- 管理员审核模式（Admin Verify）：未批准玩家保持锁定
- 未验证玩家行为阻断（移动、交互、聊天、指令、背包、伤害等）
- 背包满时自动暂存物品，验证后自动归还（保留 NBT/附魔/耐久）
- 可选隐藏未验证玩家
- 成功音效仅本人可听 + 可配置全服公告
- 书本内容/颜色全部通过 YAML 配置
- 登录信息记录（IP / 时间 / 每次登录盐）
- 明文与加密（PBKDF2）数据分区存储，可运行时切换
- 安全限制：登录限速、输入长度限制、验证超时踢出
- 内置本地 Web 管理面板（纯 Java 生成页面，无外部 HTML 文件）

---

## Commands / 命令

主命令 `/dwgxverify`（别名 `/dwgx`）

**Players / 玩家：**

| Command | Description |
|---------|-------------|
| `/dwgxverify` | 发放验证书本（自动判断登录/注册） / Give verification book |
| `/dwgxverify login <account> <password>` | 直接登录 / Direct login |
| `/dwgxverify reg <account> <password> [confirm]` | 直接注册 / Direct register |
| `/dwgxverify resetpass` | 申请重置密码 / Open reset request |

**Admin / 管理员（需要 `dwgxverify.admin` 权限，默认 OP）：**

| Command | Description |
|---------|-------------|
| `/dwgxverify toggle` | 开/关验证 / Enable/disable verification |
| `/dwgxverify chat <on\|off>` | 未验证是否允许聊天 / Allow chat before verify |
| `/dwgxverify sound <Sound> [vol] [pitch]` | 设置成功音效 / Set success sound |
| `/dwgxverify hide <on\|off>` | 隐藏未验证玩家 / Hide unverified players |
| `/dwgxverify encryption <true\|false>` | 切换 HASHED/PLAINTEXT 模式 |
| `/dwgxverify adminverify <on\|off>` | 开/关管理员审核 / Toggle admin approval |
| `/dwgxverify approve <player\|uuid>` | 审核通过 / Approve player |
| `/dwgxverify unapprove <player\|uuid>` | 取消审核 / Unapprove player |
| `/dwgxverify reset <approve\|reject> <player\|uuid>` | 处理重置请求 / Handle reset |
| `/dwgxverify reloadconfig` | 重载配置并同步 Web / Reload config |

---

## Web Admin Panel / Web 管理面板

- 默认地址 `http://127.0.0.1:1337`，默认仅本机访问（`web.local-only`）
- 支持查看用户、搜索分页、审核/反审核、处理重置请求、切换配置
- Basic Auth 认证（`web.auth.username` / `web.auth.password`，默认密码 `change-me`，部署前改掉）
- Default at `http://127.0.0.1:1337`, local-only by default
- User list, search, pagination, approve/unapprove, reset handling, config toggles
- Basic Auth (`web.auth.username` / `web.auth.password`; default password is `change-me` — change it before deploying)

---

## Tech Stack / 技术栈

- Language: Java 21
- Build: Gradle 8.8, `xyz.jpenilla.run-paper` 2.3.1
- API: `io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT` (compileOnly), `api-version: 1.21`
- No external runtime dependencies — web panel and pages are generated in pure Java

---

## Project Structure / 项目结构

```
build.gradle, settings.gradle          # Gradle build
.github/workflows/ci.yml                # CI: JDK 21 + gradle build on push/PR
src/main/resources/
  plugin.yml                            # Plugin descriptor & command/permission
  config.yml                            # Default config (bundled)
src/main/java/CNM/dwgx/l2111pageverify/
  L2111pageloginverify.java             # JavaPlugin entry point
  manager/VerifyModuleManager.java      # Wires up services on enable
  manager/VerificationManager.java
  VerificationListener.java             # Blocks unverified player actions
  VerificationEnforcer.java
  VerificationBookService.java          # Builds/handles the verification book
  NmsBookOpener.java                    # Opens the book UI
  command/VerifyCommand.java            # /dwgxverify command handling
  web/WebAdminServer.java               # Built-in local web admin panel
  UserStore.java, UserRecord.java       # Account persistence (users.yml)
  ResetRequestStore.java                # Reset requests (resets.yml)
  LogsStore.java                        # Event logs (logs.yml)
  PasswordMode.java, NoticeType.java    # Enums
```

---

## Getting Started / 快速开始

**Prerequisites / 前置条件：** JDK 21, Gradle 8.8

<!-- TODO: 确认 gradle-wrapper.jar 是否已随仓库提供；当前文件树仅见 gradle-wrapper.properties -->

**Build / 构建：**

```bash
gradle build
# 或使用 wrapper（若完整）/ or with wrapper (if complete):
./gradlew build      # Linux/macOS
gradlew.bat build    # Windows
```

产物输出到 `build/libs/`。Output goes to `build/libs/`.

**Run / 运行：**

将生成的 jar 放入服务端 `plugins/` 目录后重启即可。
Drop the jar into the server's `plugins/` folder and restart.

**Local test / 本地测试：**

```bash
gradle runServer
```

通过 `run-paper` 插件启动 1.21 测试服。Starts a Paper 1.21 test server via the `run-paper` plugin.

---

## Configuration / 配置

所有配置在 `config.yml`，关键项：

| Key | Description |
|-----|-------------|
| `enabled` | 插件总开关 |
| `encryption-enabled` | 加密模式开关 |
| `admin-verify-enabled` | 管理员审核开关 |
| `require-online-mode` | 要求服务器 online-mode=true |
| `allow-chat-before-verify` | 验证前允许聊天 |
| `hide-unverified` | 隐藏未验证玩家 |
| `auto-open-book` | 自动打开验证书本 |
| `security.*` | 限速、长度限制、超时踢出、PBKDF2 迭代次数 |
| `web.*` | Web 面板绑定地址/端口/认证 |
| `sound.*` | 成功音效配置 |
| `book.*` | 书本内容与颜色 |
| `messages.*` | 提示文本 |

**Data files / 数据文件**（插件数据目录下）：
- `users.yml` — 用户数据（`data.hashed` / `data.plain`）
- `resets.yml` — 重置请求记录
- `logs.yml` — login / pending / actions 事件日志

---

## Status / 状态

个人项目，版本 `1.0-SNAPSHOT`，活跃开发中。
Personal project, version `1.0-SNAPSHOT`, actively developed.

## License / 许可证

MIT License. Copyright (c) 2026 dwgx. See [`LICENSE`](LICENSE).
