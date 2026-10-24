# Unofficial Backport Voxy

**Voxy for Minecraft 1.20.1 (Fabric), with LoDs sent by the server, background terrain generation and one-click impact presets.**

> [!WARNING]
> This is an **unofficial** project. It is **not made, endorsed or supported by Cortex** (the author of Voxy).
> Do **not** ask for help on Cortex's Discord or on the official Voxy pages about this version.
> Problems with this version go to the [issues of this repository](https://github.com/tkettela/unofficial-backport-voxy/issues).

🇫🇷 [Version française](README.fr.md)

---

## Table of contents

- [What is this?](#what-is-this)
- [Features](#features)
- [Requirements](#requirements)
- [Getting the jar](#getting-the-jar)
- [Installation](#installation)
- [Presets and settings](#presets-and-settings)
- [Commands](#commands)
- [How it works](#how-it-works)
- [Performance](#performance)
- [Compatibility](#compatibility)
- [Troubleshooting / FAQ](#troubleshooting--faq)
- [Differences from the official Voxy](#differences-from-the-official-voxy)
- [Credits and licenses](#credits-and-licenses)

---

## What is this?

[Voxy](https://modrinth.com/mod/voxy) by **Cortex** renders the world far beyond the normal render distance using
LoDs (Levels of Detail: simplified versions of distant chunks). The official Voxy only supports recent Minecraft
versions and only shows terrain **you have already explored yourself**.

This project:

1. runs Voxy on **Minecraft 1.20.1 with Fabric and Sodium 0.5.13** (based on the multiversion port by
   [m3t4f1v3](https://github.com/m3t4f1v3/voxy));
2. adds **server side LoDs**: on a multiplayer server, the server sends the distant terrain to every player, so
   everyone sees hundreds of chunks around them without exploring first;
3. lets the server **generate never visited terrain in the background** around players;
4. adds **presets** so every player picks how much Voxy may use of their computer, in one click.

The renderer itself (LoD format, meshes, shaders, visual quality) is the same as Voxy 0.2.15-beta.

## Features

### 🌍 LoDs sent by the server (multiplayer)
- The server reads the chunks from its world (from memory for loaded chunks, from the region files otherwise) and
  sends them to the players, who turn them into LoDs locally.
- Closest chunks first, in rings all around the player.
- **Adaptive speed**: the send rate follows each player's connection (it slows down as soon as the network buffer
  fills, so gameplay packets are never stuck behind LoDs), the disk reads follow the server tick time (it slows down
  before the server lags), and the client asks for less when its own conversion queue is full.
- **Caches on both sides**: the client remembers what it already received, the server remembers what is on disk.
  When you come back, only the chunks that changed are sent again. The server cache survives restarts.
- **Live updates**: when someone builds or breaks blocks, the players who see that place as a LoD get the change
  (at most once every 30 seconds per chunk, so farms and redstone do not flood the network).
- Chunks that did not exist yet when a player asked for them are sent **as soon as they are generated**.
- Clear **version mismatch** message if the client and the server do not use the same jar.

### ⛏️ Background terrain generation (server)
- The server generates the terrain nobody visited yet around players, so the LoD view fills up without
  pre-generating the world with Chunky.
- Around 6000 chunks per minute on a 6 core / 12 thread CPU with the server tick staying around 7 ms (measured).
- Slows down automatically if the server tick time goes up, respects the **world border**, can be paused in game.
- Off by default in singleplayer (so your saves do not grow without you knowing), on by default on dedicated servers.

### 🎚️ Impact presets
- **Client**: Auto, Low, Medium, High, Ultra, Custom in the Voxy settings page. Auto detects your graphics card,
  processor and memory on every start. Changing any detail switches to Custom and keeps your values.
- **Server**: auto, low, balanced, high, max (`/voxylod preset`). Auto is chosen from the server CPU and memory.
- Everything can be changed **in game**, applied immediately, no restart.

### ✨ Other additions
- **Shaders**: works with Iris 1.7.6 and shader packs that support Voxy (tested with Complementary Reimagined),
  including the newer near/far plane uniforms backported from upstream Voxy.
- **No more seams between water and LoDs** (adapted from [voxy-seam-fix](https://modrinth.com/mod/voxy-seam-fix)).
- **LoDs in the Nether fog** option (the thick fog no longer hides all LoDs).
- **Store LoDs by server name** option (LoDs survive a server address change).
- **F6** toggles Voxy rendering in game, **F3** shows the server LoD status.
- Singleplayer: the integrated server streams the save to Voxy like a dedicated server would.

## Requirements

| | Client (player) | Server |
|---|---|---|
| Minecraft | 1.20.1 | 1.20.1 |
| Mod loader | Fabric Loader ≥ 0.14.22 | Fabric Loader ≥ 0.14.22 |
| Java | **21** | **21** |
| [Fabric API](https://modrinth.com/mod/fabric-api) | required (0.92.x) | required (0.92.x) |
| [Sodium](https://modrinth.com/mod/sodium) | **exactly 0.5.13** | not needed |
| This mod | required | required for server LoDs |
| Graphics | OpenGL 4.6 GPU (NVIDIA / recent AMD) | — |
| [Iris](https://modrinth.com/mod/iris) | optional, 1.7.x for shaders | not needed |

The **same jar** goes on the client and on the server. A server without this mod still works for players who have
it, they just get no server LoDs (only what they explore, like the official Voxy).

## Getting the jar

No ready-made jar is attached to releases, because Voxy's license does not allow redistributing it. You can get one
in a few clicks in two ways.

### Option 1: download the build made by GitHub (easiest)

Every change to this repository is built automatically by GitHub.

1. Sign in to GitHub (a free account is enough).
2. Open the [**Actions** tab](https://github.com/tkettela/unofficial-backport-voxy/actions/workflows/build.yml) of
   this repository.
3. Click the most recent run with a green check mark ✅.
4. At the bottom of the page, under **Artifacts**, click **unofficial-backport-voxy-1.20.1-fabric**.
5. You get the jar (`voxy-0.2.15-beta+1.20.1-fabric.jar`), if it comes as a `.zip`, extract it.

Builds are kept for 90 days. If the list is empty or too old, anyone with write access can start a new one with the
**Run workflow** button on the same page.

### Option 2: build it yourself with one double click

1. Install **Java 21** (for example [Temurin 21](https://adoptium.net/temurin/releases/?version=21), take the
   `.msi` installer on Windows and tick "Set JAVA_HOME").
2. Download this repository: green **Code** button → **Download ZIP**, then extract it
   (or `git clone https://github.com/tkettela/unofficial-backport-voxy`).
3. Run the build script:
   - **Windows**: double click `build-windows.bat`
   - **Linux / macOS**: open a terminal in the folder and run `sh build.sh`
4. Wait. The first build downloads everything it needs (Gradle, Minecraft, dependencies) and takes about 5 minutes,
   later builds take less than a minute.
5. The jar is in the `output` folder (the Windows script opens it for you).

### Option 3: command line

```sh
./gradlew :1.20.1-fabric:build
```

The jar is `versions/1.20.1-fabric/build/libs/voxy-0.2.15-beta+1.20.1-fabric.jar` (ignore the `-sources.jar`).

## Installation

### Player (client)
1. Create a Minecraft **1.20.1** instance with **Fabric** (Modrinth App, Prism Launcher, CurseForge app, or the
   official launcher with the [Fabric installer](https://fabricmc.net/use/installer/)).
2. In the instance settings, select **Java 21** and give it at least 4 GB of memory (6 to 8 GB recommended).
3. Put in the `mods` folder: this mod's jar, Fabric API, Sodium **0.5.13**.
4. Optional: Iris 1.7.6 for shaders, plus a shader pack with Voxy support in `shaderpacks`.
5. Start the game, Voxy is in **Options → Video Settings → Voxy**.

### Server
1. A Fabric **1.20.1** server running on **Java 21**.
2. Put this mod's jar and Fabric API in the server's `mods` folder (Sodium and Iris do **not** go on the server).
3. Start it once, `config/voxy-server.json` is created. The defaults are fine for most servers.
4. Players need the **same jar** as the server.

### Singleplayer / LAN
Nothing to do: the integrated server streams your save to Voxy. Background generation is off in singleplayer, turn
it on with `/voxylod config auto_generate_on_integrated_server true` if you want it.

## Presets and settings

### Client presets (Options → Video Settings → Voxy → "Voxy impact (preset)")

| Preset | LoD render distance | LoD detail (subdivision) | Voxy threads | Ambient occlusion | Server LoD radius |
|---|---|---|---|---|---|
| Low | 128 chunks | 128 (coarse) | cores ÷ 4 | basic | 128 chunks |
| Medium | 256 chunks | 96 | cores ÷ 3 | auto | 256 chunks |
| High | 512 chunks | 64 | cores ÷ 1.5 | better | 512 chunks |
| Ultra | 1024 chunks | 40 (fine) | cores − 2 | best | 1024 chunks |
| **Auto** (default) | picked on every start from your GPU memory, CPU cores and Java memory, takes the full radius the server offers | | | | |
| Custom | your own values (any detail you change by hand switches to Custom) | | | | |

Auto picks Low below 4 GB of video memory / 4 cores / 3 GB of Java memory, Medium below 6 GB / 6 cores / 4 GB,
High below 10 GB / 12 cores / 6 GB, Ultra above.

Other client options on the same page: enable Voxy, chunk ingest, **Server LoDs** (on/off), **Server LoD radius**,
**Store LoDs by server name**, **Fix LoD water seams**, **LoDs in thick fog** (Nether), rendering, fog, SSAO.

### Server presets (`/voxylod preset <name>`)

| Preset | LoD radius | Generation radius | Chunks generating at once | Disk reads per tick | Worker threads | Target tick time |
|---|---|---|---|---|---|---|
| low | 128 | 64 | 2 | 64 | 2 | 35 ms |
| balanced | 256 | 128 | auto | 256 | auto | 40 ms |
| high | 384 | 192 | auto | 384 | auto | 45 ms |
| max | 512 | 256 | 16 | 512 | auto | 48 ms |
| **auto** (default) | low if ≤ 2 cores or < 3 GB of memory, high if ≥ 8 cores and ≥ 10 GB, balanced otherwise. Detected again on every start | | | | | |
| custom | set as soon as one of these values is changed by hand | | | | | |

"auto" for chunks generating at once means 2 per CPU thread (4 to 32), for worker threads a third of the threads (2 to 8).

### Server settings (`config/voxy-server.json`, or `/voxylod config <setting> <value>` in game)

| Setting | Default | What it does |
|---|---|---|
| `enabled` | `true` | Send LoDs to players at all |
| `enabled_on_integrated_server` | `true` | Also in singleplayer / LAN |
| `max_radius_chunks` | 256 | Largest radius (in chunks) sent to a player |
| `max_chunk_reads_per_tick` | 256 | Upper bound of disk reads per tick, lowered automatically when the server lags |
| `max_in_flight_per_player` | 512 | Upper bound of chunk requests a client may have outstanding |
| `max_bytes_per_second_per_player` | 0 | Bandwidth limit per player, 0 = only limited by the player's connection |
| `worker_threads` | 0 (auto) | Threads reading and encoding chunks |
| `target_tick_time_ms` | 40 | Server tick time above which LoDs and generation slow down |
| `auto_generate` | `true` | Generate never visited terrain around players |
| `auto_generate_radius` | 128 | Generation radius (also limited by `max_radius_chunks`) |
| `auto_generate_max_concurrent` | 0 (auto) | Chunks generating at the same time |
| `auto_generate_on_integrated_server` | `false` | Also generate in singleplayer / LAN |
| `live_updates` | `true` | Send block changes to players that see them as LoDs |
| `live_update_min_interval_seconds` | 30 | Minimum time between two updates of the same chunk |
| `persist_timestamp_cache` | `true` | Keep the server cache over restarts (`world/data/voxy_lod_cache.bin`) |
| `preset` | `auto` | See server presets |

Generated chunks are saved like normal chunks, so the world grows on disk as it is generated.

## Commands

All `/voxylod` commands need operator level 2, or being the owner of a singleplayer / LAN world (cheats not needed).

| Command | Effect |
|---|---|
| `/voxylod status` | Tick time, players, send rate, generation progress |
| `/voxylod preset` | Show the current preset |
| `/voxylod preset <auto\|low\|balanced\|high\|max>` | Apply a preset (saved) |
| `/voxylod config` | List every setting with its value |
| `/voxylod config <setting>` | Show one setting |
| `/voxylod config <setting> <value>` | Change a setting, applied immediately and saved |
| `/voxylod generation pause` / `resume` | Stop / restart background generation |
| `/voxylod reload` | Reload `config/voxy-server.json` after editing it by hand |
| `/voxylod cache clear` | Forget the server cache (everything is read from disk again) |

Keys: **F6** toggles Voxy rendering, **F3** shows a "Server LoD" line (receive rate, requests in flight, cache).

## How it works

```
 client                                              server
 ──────                                              ──────
 joins, announces the voxy channels  ───────────▶    sends protocol version, radius, request limit
 asks for chunks in rings around     ───────────▶    for each request:
 the player (with the timestamp of                     · loaded and changed  → encoded from memory
 the copy it already has)                              · cached as unchanged → "unchanged" (no disk read)
                                                       · otherwise read from the region file off thread
 converts the sections into LoDs     ◀───────────    "data" / "unchanged" / "missing"
 (Voxy ingest, off thread)
                                     ◀───────────    "push": chunks changed by players, and chunks that
                                                      finished generating after the client asked for them
```

- Network channels `voxy:lod_config`, `voxy:lod_request`, `voxy:lod_chunk`, protocol version 2. The packets carry the
  raw block/biome palettes and the light of each section, Minecraft's own network compression applies.
- Chunks inside the vanilla view distance are converted by Voxy the normal way, the server only sends what is
  outside (plus the 3 outer rings, which Voxy cannot convert from vanilla data because their neighbours are missing).
- Generation adds a temporary ticket on never generated chunks, lets Minecraft generate them to the end, sends them
  to the players and keeps the most recent ones loaded for a while so their half generated neighbours are not
  saved and reloaded over and over.

## Performance

Measured on a Ryzen 5 9600X (6 cores / 12 threads), RTX 5070, NVMe SSD, test server with 4 GB of memory:

| | Result |
|---|---|
| Streaming already generated terrain | about 1200 to 1300 chunks/s to one player (limited by the client converting LoDs) |
| Background generation | about 6000 chunks/min (3× the first version), server tick around 7 ms, no "Can't keep up" |
| Fresh area after teleporting | LoDs up to the horizon in all directions after about 90 s |

Things that were measured and **did not help** generation on 1.20.1 (so they are not recommended for that):
C2ME, Noisium and Starlight (the limit is how Minecraft 1.20.1 schedules generation, not raw CPU), C2ME's threaded
world generation (2× slower).

## Compatibility

| Mod | Status |
|---|---|
| Sodium 0.5.13 | required, other versions are refused on purpose |
| Iris 1.7.6 + Complementary Reimagined r5.9.3 | tested, works |
| Fabric API 0.92.6 | tested |
| FerriteCore 6.0.1, ModernFix 5.25.2, ImmediatelyFast 1.5.5 | tested together, work (less memory, faster UI) |
| Lithium | works |
| Chunky | works (pre-generated worlds are streamed too) |
| C2ME, Noisium, Starlight | load fine, no measured benefit with this mod |
| Nvidium | **incompatible**: its 1.20.1 version requires Sodium 0.5.7/0.5.8 |
| Distant Horizons | not tested, both are LoD renderers, do not use both |
| Official Voxy / other Voxy builds | do not install together, this mod **is** Voxy (mod id `voxy`) |

## Troubleshooting / FAQ

**The game crashes with "class file version 65"**: the game runs on an older Java, select Java 21 in your launcher.

**"this server uses another version of the mod"** in chat: the client and the server do not have the same jar.
Use the same build on both.

**Voxy does not show anything**: check Sodium is exactly 0.5.13, and that Voxy is enabled (F6). Very old AMD and
Intel graphics are not supported by Voxy.

**Distant terrain is missing right after arriving somewhere new**: the server is generating it, it appears within
1 to 2 minutes. `/voxylod status` shows the progress.

**"Connecting to the server..." takes minutes**: that is Minecraft waiting for Mojang's servers (server blocklist
download) before connecting, not this mod.

**My computer struggles**: choose the Low or Medium preset. **The server struggles**: `/voxylod preset low` or
`/voxylod generation pause`.

**Where are the LoDs stored?** Client: `.minecraft/.voxy/` (per server address, or per server name with the
option). Server: only its cache file in `world/data/voxy_lod_cache.bin`.

## Differences from the official Voxy

| | Official Voxy (Cortex) | Unofficial Backport Voxy |
|---|---|---|
| Minecraft version | recent versions | 1.20.1 Fabric |
| Distant terrain in multiplayer | only what you explored yourself | sent by the server |
| Never visited terrain | needs pre-generation | generated in the background by the server |
| Other players' builds | not updated until you go there | live updates |
| Runs on a dedicated server | no (client mod) | yes, same jar |
| Settings | manual | presets (client and server) + manual + in game commands |
| Water/LoD seams, Nether fog, storage by server name, F6 toggle | — | included |
| Renderer, LoD format, visual quality | — | **identical** |

## Credits and licenses

| Project | Author | License | Used for |
|---|---|---|---|
| [Voxy](https://github.com/MCRcortex/voxy) | **MCRcortex (Cortex)** | All rights reserved | The whole renderer, this repository is a GitHub fork of it |
| [Voxy multiversion port](https://github.com/m3t4f1v3/voxy) | m3t4f1v3 | (fork of Voxy) | 1.20.1 / Sodium 0.5 port this project is built on |
| [voxy-seam-fix](https://modrinth.com/mod/voxy-seam-fix) | HTRMC | MIT | Water surface pinning across LoD levels (code adapted, notice in `voxy-third-party-notices.txt`) |
| [voxy-extra](https://github.com/ImGRUI/voxy-extra) | ImGRUI | LGPL-3.0 | Ideas only (Nether fog, toggle key, storage by server name), reimplemented, no code copied |
| [LOD Server Support](https://github.com/VoX/lod-server-support) | VoX | MIT | Idea only (send chunks once generated), no code copied |
| Voxygen | — | custom (no derivatives) | **Not used**, generation is an independent implementation |
| [Sodium](https://github.com/CaffeineMC/sodium) | CaffeineMC | PolyForm Shield 1.0.0 | Required dependency, not included |
| [Iris](https://github.com/IrisShaders/Iris) | IrisShaders | LGPL-3.0 | Optional dependency, not included |
| [Fabric API](https://github.com/FabricMC/fabric) | FabricMC | Apache-2.0 | Required dependency, not included |
| [Complementary Reimagined](https://modrinth.com/shader/complementary-reimagined) | EminGT | Complementary license | Shader pack used for testing, not included |

**Voxy is © MCRcortex, all rights reserved.** This repository is a fork made with GitHub's fork feature and is
published for people who want to build and use it themselves. **No jar is redistributed in releases.** All rights
to Voxy stay with its author: if Cortex asks for this repository to be taken down, it will be.
The additions of this fork (server LoDs, generation, presets, commands) are provided as they are, without warranty.

Server LoDs and generation are written by tkettela with the help of Claude (Anthropic).
