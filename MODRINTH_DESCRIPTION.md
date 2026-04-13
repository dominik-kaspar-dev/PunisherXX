# PunisherXX

PunisherXX is a configurable punishment plugin for Paper 1.21.x focused on fast moderation workflows.

It supports custom punish types, warning escalation, and built-in ban, kick, and mute logic, while keeping everything editable in config.

## Why PunisherXX

- Config-driven punish flows for different rule breaks
- Built-in action keywords for warning, kicking, banning, unbanning, muting, and unmuting
- Warning escalation rules (example: 3 warnings -> temporary ban)
- Timed punishments with human-friendly durations like 30m, 12h, 7d, 2w, or perm
- Permission-node friendly design for LuckPerms and similar permission plugins
- Persistent punishment data stored in data.yml

## Commands

- /punish <type> <player> [reason]
- /punish reload
- /warn <player> [time] [reason]
- /pban <player> <time> [reason]
- /pkick <player> [reason]
- /pmute <player> <time> [reason]
- /punban <player>
- /punmute <player>

## Permissions

Default nodes:

- punisher.punish
- punisher.warn
- punisher.ban
- punisher.kick
- punisher.mute
- punisher.unban
- punisher.unmute
- punisher.reload

Dynamic node template:

- punisher.punish.%type%

You can fully customize node names in config.yml.

## Config Style

Define punish types as either direct action lists or structured blocks with custom permissions.

Example:

```yml
punish:
  xray:
    permission: punisher.punish.xray
    actions:
      - "kill %player%"
      - "clear %player%"
      - "pban 30d %reason%"
```

Warning escalation example:

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

## Placeholders

Common placeholders in actions and messages:

- %player%
- %reason%
- %type%
- %actor%
- %duration%
- %count%

## Compatibility

- Server software: Paper (1.21.x)
- Java: 21

## Install

1. Build the plugin or download the release jar.
2. Put the jar into your server plugins folder.
3. Start the server once to generate config files.
4. Edit config.yml for your punish logic and permissions.
5. Use /punish reload after changes.

## Notes

PunisherXX is intended for server staff moderation. Review your punish commands and escalation thresholds before using in production.
