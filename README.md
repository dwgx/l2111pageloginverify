# l2111pageloginverify

A Paper 1.21.11 login/registration verification plugin using a server-issued verification book.

## Features
- Register/login by signing the verification book
- Full action block before verification (movement, interaction, chat, commands, inventory, damage, etc.)
- Safe-store items when inventory is full, auto-restore after verification (keeps NBT/enchant/durability)
- Option to hide unverified players
- Success sound + ActionBar message
- Book content/colors fully configurable in YAML
- Login info tracking (IP / time / per-login salt)
- Separate datasets for encrypted vs plaintext mode (switchable)
- Login/pending history kept in a separate `logs.yml` with rolling limits
- Built-in local web admin panel (pure Java, no external HTML files)

## Commands
- `/dwgxverify`
  - Give verification book (login or register depending on record)
- `/dwgxverify toggle`
  - Enable/disable verification
- `/dwgxverify chat <on|off>`
  - Allow/disallow chat before verification
- `/dwgxverify sound <Sound> [volume] [pitch]`
  - Set success sound
- `/dwgxverify hide <on|off>`
  - Hide/show unverified players
- `/dwgxverify encryption <true|false>`
  - Toggle encryption (true=HASHED / false=PLAINTEXT)
  - Datasets are stored separately

## Web Panel
- Enabled by default on `127.0.0.1:1337` (change via `web.bind` / `web.port`)
- View users and toggle modes from the browser
- Approve/unapprove users when admin verification is enabled
- Search + pagination for large servers
- Optional local-only access (`web.local-only`)
- Basic Auth via `web.auth.username` / `web.auth.password`

## Configuration
See `config.yml`. Common keys:
- `encryption-enabled`: true/false
- `log-pending`: log pending store/restore
- `log-login-info`: log IP/salt updates
- `book.*`: book content/colors

## Data (users.yml)
Stored under separate sections:
- `data.hashed` for encrypted mode
- `data.plain` for plaintext mode
- Each user stores register time/IP plus last-login info; historical login/pending events live in `logs.yml`

## Encoding Notes
- Java source files must be UTF-8 **without BOM** (javac requirement).
- `config.yml` is saved as UTF-8 **with BOM** for Windows editors.
