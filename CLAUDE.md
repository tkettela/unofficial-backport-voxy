# Voxy 1.20.1 Fabric + server LoD streaming

Personal build of Voxy (LoD renderer) for Minecraft 1.20.1 Fabric, based on m3t4f1v3/voxy (`multiversion` branch,
Voxy 0.2.15-beta, Stonecutter multi-version build). Work happens on the `server-lod` branch.
Voxy is "All rights reserved, do not redistribute": keep this repository private and never publish builds.

## Build
- Only the 1.20.1 Fabric variant is used: `./gradlew :1.20.1-fabric:build` (Java 21, first build ~5 min)
- Output: `versions/1.20.1-fabric/build/libs/voxy-0.2.15-beta+1.20.1-fabric.jar` (same jar for client and server)
- Dev client: `./gradlew :1.20.1-fabric:runClient` (needs a GPU, not possible in cloud sessions)
- Stonecutter: shared code in `src/main`, `//?` conditional comments select versions/loaders. Do not break the
  1.21.1/forge variants when editing shared files, put 1.20.1 fabric only code in `versions/1.20.1-fabric/src/fabric`

## Our additions (1.20.1 fabric only unless noted)
- `versions/1.20.1-fabric/src/fabric/java/me/cortex/voxy/commonImpl/network/`
  - `ServerLodStreamer` server side: answers chunk requests from memory or region files, adaptive send rate
    (netty writability), adaptive read budget (tick time), timestamp cache, live updates, `/voxylod` hooks
  - `ServerLodGenerator` background terrain generation around players (tickets, world border, look direction first)
  - `ServerLodCommand` `/voxylod status|config|generation|reload|cache`, settings apply live
  - `ServerLodConfig` `config/voxy-server.json` (migrations via `config_version`)
  - `ServerLodCachePersistence`, `ServerChunkChangeTracker`, `VoxyNetworking` (protocol version 2)
  - `mixin/` (`serverlod.voxy.mixins.json`): block change tracking, netty channel accessors, copy chunk tag before upgrade
- `versions/1.20.1-fabric/src/fabric/java/me/cortex/voxy/client/network/` `ClientLodReceiver` (spiral requests,
  adaptive window, client timestamp cache), `VoxyToggleKey` (F6)
- Shared code: upstream fixes (ActiveSectionTracker unload race, ingest world refs), shader define 3
  (vxNearPlane/vxFarPlane, useDynamicFarPlane), lod water seam fix (from voxy-seam-fix, MIT, see
  `voxy-third-party-notices.txt`), nether fog option, storage by server name, extra F3 lines hook
- Entry points are registered as extra fabric entrypoints in `versions/1.20.1-fabric/src/fabric/resources/fabric.mod.json`

## Pitfalls already hit
- `ServerChunkCache.getChunkFuture` blocks when called on the server thread (watchdog crash): call it from a worker
- The IO worker hands out the tag of a pending save; never upgrade/mutate stored chunk tags in place (copy first)
- Fabric `S2CPlayChannelEvents.REGISTER` fires during connection setup: defer work to the next tick
- Sodium mixins with `remap = false` must match methods by name only (descriptors are not remapped)
- Mixin targets in `src/main` files use CRLF line endings, prefer exact edits over regex on them
- Protocol changes must bump `VoxyNetworking.PROTOCOL_VERSION` (client shows a mismatch message)
