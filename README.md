# PunisherXX

PunisherXX is a Paper 1.21.x plugin with configurable punishment flows, warning escalation, and LuckPerms-friendly permission nodes.

## Features

- Config-driven `/punish` command with custom punishment types.
- Built-in `pban`, `pkick`, `pmute`, and `pwarn` action keywords.
- `/warn <player> [time] [reason]` with timed warnings.
- Automatic escalation (example: 3 warnings => 7 day ban).
- Built-in `/pban`, `/pkick`, and `/pmute` commands.
- Persistent punishments in `plugins/PunisherXX/data.yml`.

## Commands

- `/punish <type> <player> [reason]`
- `/punish reload`
- `/warn <player> [time] [reason]`
- `/pban <player> <time> [reason]`
- `/pkick <player> [reason]`
- `/pmute <player> <time> [reason]`

## LuckPerms Permissions

All nodes are configurable in `config.yml`:

- `punisher.punish`
- `punisher.punish.<type>` (from template)
- `punisher.warn`
- `punisher.ban`
- `punisher.kick`
- `punisher.mute`
- `punisher.reload`

## Example Config Style (your requested format)

You can use direct command lists:

```yml
punish:
  xray:
    - "kill %player%"
    - "clear %player%"
    - "ban 30d %player% %reason%"
```

Or use built-in plugin logic actions:

```yml
punish:
  xray:
    permission: punisher.punish.xray
    actions:
      - "kill %player%"
      - "clear %player%"
      - "pban 30d %reason%"
```

## Warning Escalation Example

```yml
warnings:
  default-duration: 30d
  escalation:
    "3":
      duration: 7d
      reason: Reached 3 active warnings.
    "5":
      duration: 30d
      reason: Reached 5 active warnings.
```

## Build

From `punisherXX` directory:

```powershell
..\apache-maven-3.9.14\bin\mvn -DskipTests package
```

Jar output should be in `target/`.
