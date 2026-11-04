![Banner](images/banner.png)

## Overview

ThEnderButt adds a configurable Ender Pearl Launcher to your server. Players
can use it to ride and launch an Ender Pearl, with settings to tailor the item
and its behavior to your server.

### Features

- Configure the launcher's inventory slot, quantity, material, display name,
  lore, appearance, permission, and cooldown.
- Choose which worlds allow the launcher and whether it is given on join,
  respawn, or after an inventory clear.
- Configure item protections, whether using it consumes the item, and whether
  players can dismount.
- Use PlaceholderAPI values in supported messages and item text when
  PlaceholderAPI is installed.
- Optionally integrate with ajParkour to hide the launcher while a player is
  participating in a parkour game.

### Requirements

- Java 8 or newer
- Minecraft 1.8 or newer
- PlaceholderAPI and ajParkour are optional integrations.

### Installation

1. Download the plugin JAR from the
   [SpigotMC resource page](https://www.spigotmc.org/resources/thenderbutt.140246/).
2. Place the JAR in your server's `plugins` folder.
3. Restart the server.

### Commands

- `/enderbutt help` — show the available commands.
- `/enderbutt reload` — reload the plugin configuration and messages. Requires
  `thenderbutt.reload`.
- `/enderbutt give <player>` — give the launcher to a player. Requires
  `thenderbutt.give`.

Both permissions are granted to server operators by default.

# Plugin Metrics
![Metrics](https://bstats.org/signatures/bukkit/ThEnderButt.svg)

# Support
For support join our [Discord server](https://discord.gg/n6uDZTJ7QR) or open an issue on GitHub.