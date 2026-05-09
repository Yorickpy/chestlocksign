## Chest SignLock 0.1.1

This update focuses on protection consistency, safer sign behavior, and quality-of-life fixes.

### Added

- Added beacon support. A protected beacon cannot be stolen or destroyed directly, while the beacon base structure remains unprotected.
- Added protection for the support block directly under private doors, preventing players from breaking the block below a protected door to pop it.
- Automatic sign placement now keeps the wood type of the sign item being used instead of always placing oak signs.

### Fixed

- Fixed hidden UUID data granting access after an operator changed the visible username on a sign.
- Fixed owner sign protection so only the owner listed on the main `[Private]` sign, or an operator, can break that main sign.
- Fixed normal decorative signs attached to private blocks being protected by mistake. Only `[Private]` and `[More Users]` signs are now protected.
- Confirmed decorative signs do not grant access, even if player names are written on them.

### Notes

- This version keeps the same mod id for compatibility with existing protected signs and stored sign data.
- If you update a local server manually, make sure only one Chest SignLock jar is present in the `mods` folder.
