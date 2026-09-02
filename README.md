# ZenithProxyWitherKiller

A [ZenithProxy](https://github.com/rfresh2/ZenithProxy) plugin that automatically summons and kills withers.

The bot continuously places soul sand toward a fixed target coordinate, waits for withers to spawn, then engages KillAura to eliminate them. The cycle repeats automatically.

## Features

- **Auto Placement** - Places soul sand blocks toward a configurable target position
- **Auto Kill** - Enables KillAura targeting only withers when spawn threshold is reached
- **Cleanup** - Automatically removes obstructing blocks (pistons, redstone, etc.) in front of the bot
- **Configurable** - Detection range, spawn wait time, fight timeout, and protected blocks are all configurable
- **KillAura Snapshot** - Saves and restores your KillAura settings when the module is disabled
- **Protected Blocks** - Redstone components, dispensers, droppers, and other machines are protected from cleanup by default

## Requirements

- [ZenithProxy](https://github.com/rfresh2/ZenithProxy) for Minecraft 1.21.4
- Java 21+

## Installation

1. Download the latest `ZenithProxyWitherKiller-1.0.0.jar` from [Releases](https://github.com/AeiouJx/ZenithProxyWitherKiller/releases)
2. Place the jar file in your ZenithProxy `plugins/` directory
3. Restart ZenithProxy

## Commands

| Command | Description |
|---------|-------------|
| `witherKiller on/off` | Enable or disable the wither killer module |
| `witherKiller captureTarget` | Capture the block your crosshair is looking at as the placement target |
| `witherKiller target <x> <y> <z>` | Set the placement target coordinates manually |
| `witherKiller range <blocks>` | Set the wither detection range (default: 24) |
| `witherKiller requiredWithers <count>` | Set how many withers must be present before starting the fight (default: 6) |
| `witherKiller interval <ticks>` | Set the placement interval in ticks (default: 1) |
| `witherKiller spawnWait <ticks>` | Set the wait time after placing soul sand before checking for withers (default: 20) |
| `witherKiller fightTimeout <ticks>` | Set the timeout before force-starting the fight (default: 200) |
| `witherKiller protect add <blockName>` | Add a block to the protected list (won't be cleaned up) |
| `witherKiller protect remove <blockName>` | Remove a block from the protected list |
| `witherKiller protect list` | List all protected blocks |
| `witherKiller protect reset` | Reset protected blocks to defaults |

**Alias:** `wk`

## Configuration

All settings are saved in `config/kill-wither.json`. Example:

```json
{
  "witherKiller": {
    "enabled": false,
    "targetConfigured": false,
    "targetX": 0.0,
    "targetY": 64.0,
    "targetZ": 0.0,
    "witherDetectionRange": 24,
    "requiredWithersBeforeFight": 6,
    "placementIntervalTicks": 1,
    "witherSpawnWaitTicks": 20,
    "fightStartTimeoutTicks": 200,
    "protectedBlocks": [
      "REDSTONE_TORCH",
      "NOTE_BLOCK",
      "PISTON",
      "STICKY_PISTON",
      "PISTON_HEAD",
      "MOVING_PISTON",
      "OBSERVER",
      "DISPENSER",
      "DROPPER",
      "REDSTONE_WIRE",
      "REDSTONE_BLOCK",
      "REPEATER",
      "COMPARATOR"
    ]
  }
}
```

## Wither Killing Machine (Schematic)

A compatible redstone machine schematic is included in the [Releases](https://github.com/AeiouJx/ZenithProxyWitherKiller/releases) page.

**File:** `WitherKillerMachine.litematic`

This is a wither killing machine with the following features:
- **Auto Soul Sand Restocking** - Automatically replenishes soul sand supply from a storage system
- **Sorting System** - Sorts and collects drops (nether star, wither skeleton skulls, coal, etc.)
- **Compact Design** - Can be built in a standard survival world

### How to Use

1. Download `WitherKillerMachine.litematic` from the release
2. Use [Litematica](https://www.curseforge.com/minecraft/mc-mods/litematica) mod to paste the schematic in your world
3. Build the machine according to the schematic
4. Stand at the bot's position (where soul sand is placed) and run `witherKiller captureTarget` targeting the placement area
5. Enable the module with `witherKiller on`

### Machine Layout

```
                [Dispenser]
                    |
[Soul Sand Storage] -> [Feeding System] -> [Placement Area]
                    |
              [Drop Collection]
                    |
              [Sorting System]
```

The bot stands at the placement area and places soul sand toward the dispenser. The redstone machine handles restocking and collection automatically.

## Usage

1. Build the wither killing machine (or set up your own)
2. Stand at the position where you want the bot to place withers
3. Look at the target block and run `witherKiller captureTarget`
4. Enable the module with `witherKiller on`
5. The bot will automatically place soul sand, wait for withers to spawn, and kill them

## Building from Source

```bash
git clone https://github.com/AeiouJx/ZenithProxyWitherKiller.git
cd ZenithProxyWitherKiller
./gradlew build
```

The built jar will be in `build/libs/`.

## License

[CC0 1.0 Universal](LICENSE)
