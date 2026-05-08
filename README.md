# Chest SignLock

Chest SignLock is a server-side Fabric mod for Minecraft 1.21.11. It lets players lock containers and doors with signs, using a familiar `[Private]` / `[More Users]` format.

## Features

- Lockable blocks: chests, trapped chests, barrels, shulker boxes, dispensers, droppers, furnaces, blast furnaces, smokers, lecterns and all doors.
- Right-click a lockable block with a sign while not sneaking to create a protection sign automatically.
- First sign uses `[Private]` plus the owner name. Extra signs use `[More Users]` for additional allowed players.
- Double chests and doors are protected as a single linked object.
- Player UUIDs are stored in sign data when possible, while player names remain visible and usable.
- Unauthorized players cannot open protected blocks, edit protected signs or break protected blocks.
- Operators can bypass player-facing protections.
- Explosions cannot destroy protected blocks or their attached signs.
- Hoppers, hopper minecarts and copper golems cannot access protected inventories unless an attached sign contains `[Redstone]`.
- Redstone cannot open protected doors unless an attached sign contains `[Redstone]`.
- Optional debug mode logs who opened a protected block, how many signs are attached and which users are authorized.
- Built-in French and English messages.

## Commands

- `/chestlocklang fr`: switch messages to French. OP only.
- `/chestlocklang en`: switch messages to English. OP only.
- `/debuglockedchest`: toggle debug logs. OP only.

## Messages

Messages are generated in the server config folder:

```text
config/ChestLockSign/messages.yml
config/ChestLockSign/messages/fr.yml
config/ChestLockSign/messages/en.yml
```

Normal chat messages use the orange `[Chest SignLock]` prefix. Debug lines use `[Debug Signlock]`.

## Build

Requirements:

- Java 21

Build the mod:

```powershell
.\gradlew.bat build
```

The jar is generated in:

```text
build/libs/local-server-mod-0.1.0.jar
```

For the local server workflow used during development:

```powershell
.\build-and-install.bat
```

That script builds the mod and copies the jar to `../../mods`.

## Project Structure

- `src/main/java/fr/yorick/localservermod/SimpleServerMod.java`: main mod logic.
- `src/main/java/fr/yorick/localservermod/mixin/`: mixins for explosions, hoppers, copper golems and doors.
- `src/main/resources/fabric.mod.json`: Fabric metadata.
- `src/main/resources/local_server_mod.mixins.json`: mixin configuration.
- `build.gradle`: Gradle/Fabric Loom build.
- `gradle.properties`: Minecraft, Yarn, Loader, Fabric API and mod versions.

## Notes

The mod id is still `local_server_mod` to keep existing stored sign attachments compatible with current worlds. The config folder is `ChestLockSign` for easier server administration.
