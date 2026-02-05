# l2111pageloginverify

Paper 1.21.11 login/registration verification plugin using a server-issued verification book.

## Features
- Register/login by signing the verification book
- Password reset requests with admin approval (forces re-register)
- Admin verification mode (unapproved users stay locked)
- Full action block before verification (movement, interaction, chat, commands, inventory, damage, etc.)
- Safe-store items when inventory is full, auto-restore after verification (keeps NBT/enchant/durability)
- Option to hide unverified players
- Success sound + broadcast message
- Book content/colors fully configurable in YAML
- Login info tracking (IP / time / per-login salt)
- Separate datasets for encrypted vs plaintext mode (switchable)
- Security limits: login rate limiting, input length caps, verify timeout kick
- Built-in local web admin panel (pure Java, no external HTML files)

## Commands
- `/dwgxverify`
  - Give verification book (login or register depending on record)
- `/dwgxverify toggle`
- `/dwgxverify chat <on|off>`
- `/dwgxverify sound <Sound> [volume] [pitch]`
- `/dwgxverify hide <on|off>`
- `/dwgxverify encryption <true|false>`
- `/dwgxverify adminverify <on|off>`
- `/dwgxverify approve <player|uuid>`
- `/dwgxverify unapprove <player|uuid>`
- `/dwgxverify reset <approve|reject> <player|uuid>`

## Reset Flow
1. Player fills page 4 (QQ / Minecraft name / agree) and signs
2. Admin approves in Web or command
3. Old account is removed and player is locked to re-register

## Web Panel
- Default: `http://127.0.0.1:1337`
- Users list, search, pagination, approve/unapprove, reset approvals, config toggles
- Basic Auth via `web.auth.username` / `web.auth.password`
- Optional local-only access (`web.local-only`)

## Data Files
- `users.yml` (`data.hashed` / `data.plain`)
- `resets.yml`
- `logs.yml` (login / pending / actions)

## Config Highlights
- `admin-verify-enabled`
- `encryption-enabled`
- `security.verify-timeout-seconds`
- `title-fade-in-ms / title-stay-ms / title-fade-out-ms`
- `web.bind / web.port / web.local-only`
- `web.auth.*`
- `logs.max-login / logs.max-pending / logs.max-actions`
- `book.*`

## Build
```bash
./gradlew build
```

## Encoding Notes
- Java source files must be UTF-8 **without BOM**
- `config.yml` is UTF-8 **with BOM** for Windows editors
