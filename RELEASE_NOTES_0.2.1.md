# Chest SignLock 0.2.1

Compatibility update for Minecraft 26.3.

## Changes

- Updated Minecraft to 26.3, Fabric Loader to 0.19.5 and Fabric API to 0.163.0+26.3.
- Updated Fabric Loom to 1.18.4 and the Gradle wrapper to 9.7.1.
- Migrated sign handling to the Minecraft 26.3 `SignTextSlot` and mutable sign text APIs.
- Replaced the removed `SignItem` type check while keeping hanging signs excluded from automatic lock placement.
- Added support for poplar standing and wall signs.
- Preserved the `local_server_mod` mod id, attachment id and configuration paths for existing-world compatibility.

## Validation

- Clean Gradle build with Java 25.
- Dedicated Fabric 26.3 server startup with all required mixins applied.
- Verified with Fabric API 0.161.0+26.3 and 0.163.0+26.3.
