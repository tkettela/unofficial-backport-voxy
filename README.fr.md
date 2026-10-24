# Unofficial Backport Voxy

**Voxy pour Minecraft 1.20.1 (Fabric), avec les LoD envoyés par le serveur, la génération du terrain en arrière-plan et des préréglages d'impact en un clic.**

> [!WARNING]
> Ce projet est **non officiel**. Il n'est **ni fait, ni approuvé, ni supporté par Cortex** (l'auteur de Voxy).
> Ne demande **pas** d'aide sur le Discord de Cortex ni sur les pages officielles de Voxy pour cette version.
> Les problèmes de cette version se signalent dans les [issues de ce dépôt](https://github.com/tkettela/unofficial-backport-voxy/issues).

🇬🇧 [English version](README.md)

---

## Sommaire

- [C'est quoi ?](#cest-quoi-)
- [Fonctionnalités](#fonctionnalités)
- [Prérequis](#prérequis)
- [Obtenir le jar](#obtenir-le-jar)
- [Installation](#installation)
- [Préréglages et réglages](#préréglages-et-réglages)
- [Commandes](#commandes)
- [Comment ça marche](#comment-ça-marche)
- [Performances](#performances)
- [Compatibilité](#compatibilité)
- [Problèmes fréquents / FAQ](#problèmes-fréquents--faq)
- [Différences avec le Voxy officiel](#différences-avec-le-voxy-officiel)
- [Crédits et licences](#crédits-et-licences)

---

## C'est quoi ?

[Voxy](https://modrinth.com/mod/voxy), de **Cortex**, affiche le monde bien au-delà de la distance de rendu normale
grâce aux LoD (Levels of Detail : des versions simplifiées des chunks lointains). Le Voxy officiel ne fonctionne que
sur les versions récentes de Minecraft et n'affiche que le terrain **que tu as déjà exploré toi-même**.

Ce projet :

1. fait tourner Voxy sur **Minecraft 1.20.1 avec Fabric et Sodium 0.5.13** (à partir du portage multiversion de
   [m3t4f1v3](https://github.com/m3t4f1v3/voxy)) ;
2. ajoute les **LoD envoyés par le serveur** : en multijoueur, le serveur envoie le terrain lointain à chaque joueur,
   tout le monde voit des centaines de chunks autour de lui sans avoir à explorer ;
3. permet au serveur de **générer en arrière-plan le terrain jamais visité** autour des joueurs ;
4. ajoute des **préréglages** pour que chaque joueur choisisse en un clic l'impact de Voxy sur son PC.

Le moteur de rendu lui-même (format des LoD, maillages, shaders, qualité visuelle) est celui de Voxy 0.2.15-beta.

## Fonctionnalités

### 🌍 LoD envoyés par le serveur (multijoueur)
- Le serveur lit les chunks de son monde (en mémoire s'ils sont chargés, sinon dans les fichiers de région) et les
  envoie aux joueurs, qui les transforment en LoD chez eux.
- Les chunks les plus proches d'abord, en anneaux tout autour du joueur.
- **Vitesse adaptative** : l'envoi suit la connexion de chaque joueur (il ralentit dès que le tampon réseau se
  remplit, le jeu n'attend jamais derrière les LoD), la lecture disque suit le temps de tick du serveur (elle
  ralentit avant que le serveur lag), et le client demande moins quand sa propre file de conversion est pleine.
- **Caches des deux côtés** : le client se souvient de ce qu'il a déjà reçu, le serveur de ce qui est sur le disque.
  Quand tu reviens, seuls les chunks modifiés sont renvoyés. Le cache du serveur survit aux redémarrages.
- **Mises à jour en direct** : quand quelqu'un construit ou casse des blocs, les joueurs qui voient cet endroit en LoD
  reçoivent le changement (au plus une fois toutes les 30 secondes par chunk, pour que les fermes et la redstone ne
  saturent pas le réseau).
- Les chunks qui n'existaient pas encore quand un joueur les a demandés sont envoyés **dès qu'ils sont générés**.
- Message clair de **version différente** si le client et le serveur n'ont pas le même jar.

### ⛏️ Génération du terrain en arrière-plan (serveur)
- Le serveur génère le terrain jamais visité autour des joueurs, la vue LoD se remplit sans pré-générer le monde avec Chunky.
- Environ 6000 chunks par minute sur un processeur 6 cœurs / 12 threads, avec un tick serveur autour de 7 ms (mesuré).
- Ralentit tout seul si le temps de tick monte, respecte la **bordure du monde**, peut être mise en pause en jeu.
- Désactivée par défaut en solo (pour que tes sauvegardes ne grossissent pas sans que tu le saches), activée par défaut sur serveur dédié.

### 🎚️ Préréglages d'impact
- **Client** : Auto, Faible, Moyen, Élevé, Ultra, Personnalisé dans la page de réglages Voxy. Auto détecte ta carte
  graphique, ton processeur et ta mémoire à chaque démarrage. Modifier un détail passe en Personnalisé et garde tes valeurs.
- **Serveur** : auto, low, balanced, high, max (`/voxylod preset`). Auto est choisi selon le processeur et la mémoire du serveur.
- Tout se change **en jeu**, appliqué tout de suite, sans redémarrer.

### ✨ Autres ajouts
- **Shaders** : fonctionne avec Iris 1.7.6 et les packs de shaders compatibles Voxy (testé avec Complementary
  Reimagined), y compris les nouvelles infos de profondeur reprises du Voxy officiel.
- **Plus de joints entre l'eau et les LoD** (adapté de [voxy-seam-fix](https://modrinth.com/mod/voxy-seam-fix)).
- Option **LoD dans le brouillard du Nether** (le brouillard épais ne cache plus tous les LoD).
- Option **Stocker les LoD par nom de serveur** (les LoD survivent à un changement d'adresse du serveur).
- **F6** active/désactive le rendu Voxy en jeu, **F3** affiche l'état des LoD du serveur.
- Solo : le serveur intégré envoie la sauvegarde à Voxy comme le ferait un serveur dédié.

## Prérequis

| | Client (joueur) | Serveur |
|---|---|---|
| Minecraft | 1.20.1 | 1.20.1 |
| Chargeur de mods | Fabric Loader ≥ 0.14.22 | Fabric Loader ≥ 0.14.22 |
| Java | **21** | **21** |
| [Fabric API](https://modrinth.com/mod/fabric-api) | obligatoire (0.92.x) | obligatoire (0.92.x) |
| [Sodium](https://modrinth.com/mod/sodium) | **exactement 0.5.13** | inutile |
| Ce mod | obligatoire | obligatoire pour les LoD serveur |
| Carte graphique | compatible OpenGL 4.6 (NVIDIA / AMD récente) | — |
| [Iris](https://modrinth.com/mod/iris) | optionnel, 1.7.x pour les shaders | inutile |

Le **même jar** va sur le client et sur le serveur. Un serveur sans ce mod fonctionne quand même pour les joueurs
qui l'ont, ils n'ont simplement pas de LoD serveur (seulement ce qu'ils explorent, comme le Voxy officiel).

## Obtenir le jar

Aucun jar tout fait n'est publié en « release », parce que la licence de Voxy n'autorise pas à le redistribuer.
Tu peux en obtenir un en quelques clics, de deux façons.

### Méthode 1 : télécharger la version compilée par GitHub (la plus simple)

Chaque modification de ce dépôt est compilée automatiquement par GitHub.

1. Connecte-toi à GitHub (un compte gratuit suffit).
2. Ouvre l'[onglet **Actions**](https://github.com/tkettela/unofficial-backport-voxy/actions/workflows/build.yml) de ce dépôt.
3. Clique sur la compilation la plus récente avec une coche verte ✅.
4. En bas de la page, dans **Artifacts**, clique sur **unofficial-backport-voxy-1.20.1-fabric**.
5. Tu obtiens le jar (`voxy-0.2.15-beta+1.20.1-fabric.jar`). S'il arrive dans un `.zip`, décompresse-le.

Les compilations sont gardées 90 jours. Si la liste est vide ou trop ancienne, une personne avec les droits
d'écriture peut en relancer une avec le bouton **Run workflow** sur la même page.

### Méthode 2 : le compiler toi-même en un double-clic

1. Installe **Java 21** (par exemple [Temurin 21](https://adoptium.net/temurin/releases/?version=21), prends
   l'installeur `.msi` sous Windows et coche « Set JAVA_HOME »).
2. Télécharge ce dépôt : bouton vert **Code** → **Download ZIP**, puis décompresse-le
   (ou `git clone https://github.com/tkettela/unofficial-backport-voxy`).
3. Lance le script de compilation :
   - **Windows** : double-clic sur `build-windows.bat`
   - **Linux / macOS** : ouvre un terminal dans le dossier et lance `sh build.sh`
4. Patiente. La première compilation télécharge tout ce qu'il faut (Gradle, Minecraft, dépendances) et prend
   environ 5 minutes, les suivantes moins d'une minute.
5. Le jar est dans le dossier `output` (le script Windows l'ouvre pour toi).

### Méthode 3 : en ligne de commande

```sh
./gradlew :1.20.1-fabric:build
```

Le jar est `versions/1.20.1-fabric/build/libs/voxy-0.2.15-beta+1.20.1-fabric.jar` (ignore le `-sources.jar`).

## Installation

### Joueur (client)
1. Crée une instance Minecraft **1.20.1** avec **Fabric** (Modrinth App, Prism Launcher, application CurseForge, ou
   le launcher officiel avec l'[installeur Fabric](https://fabricmc.net/use/installer/)).
2. Dans les réglages de l'instance, choisis **Java 21** et donne au moins 4 Go de mémoire (6 à 8 Go conseillés).
3. Mets dans le dossier `mods` : le jar de ce mod, Fabric API, Sodium **0.5.13**.
4. Optionnel : Iris 1.7.6 pour les shaders, et un pack de shaders compatible Voxy dans `shaderpacks`.
5. Lance le jeu, Voxy est dans **Options → Paramètres vidéo → Voxy**.

### Serveur
1. Un serveur Fabric **1.20.1** qui tourne sous **Java 21**.
2. Mets le jar de ce mod et Fabric API dans le dossier `mods` du serveur (Sodium et Iris ne vont **pas** sur le serveur).
3. Démarre-le une fois, `config/voxy-server.json` est créé. Les valeurs par défaut conviennent à la plupart des serveurs.
4. Les joueurs doivent avoir **le même jar** que le serveur.

### Solo / LAN
Rien à faire : le serveur intégré envoie ta sauvegarde à Voxy. La génération en arrière-plan est désactivée en solo,
active-la avec `/voxylod config auto_generate_on_integrated_server true` si tu la veux.

## Préréglages et réglages

### Préréglages client (Options → Paramètres vidéo → Voxy → « Voxy impact (preset) »)

| Préréglage | Distance de rendu LoD | Détail des LoD (subdivision) | Threads Voxy | Occlusion ambiante | Rayon LoD serveur |
|---|---|---|---|---|---|
| Faible | 128 chunks | 128 (grossier) | cœurs ÷ 4 | basique | 128 chunks |
| Moyen | 256 chunks | 96 | cœurs ÷ 3 | auto | 256 chunks |
| Élevé | 512 chunks | 64 | cœurs ÷ 1,5 | meilleure | 512 chunks |
| Ultra | 1024 chunks | 40 (fin) | cœurs − 2 | maximale | 1024 chunks |
| **Auto** (par défaut) | choisi à chaque démarrage selon la mémoire de ta carte graphique, tes cœurs et la mémoire Java, prend tout le rayon que le serveur propose | | | | |
| Personnalisé | tes propres valeurs (tout détail modifié à la main passe en Personnalisé) | | | | |

Auto choisit Faible en dessous de 4 Go de mémoire vidéo / 4 cœurs / 3 Go de mémoire Java, Moyen en dessous de
6 Go / 6 cœurs / 4 Go, Élevé en dessous de 10 Go / 12 cœurs / 6 Go, Ultra au-dessus.

Autres options client sur la même page : activer Voxy, conversion des chunks, **LoD serveur** (oui/non),
**Rayon LoD serveur**, **Stocker les LoD par nom de serveur**, **Corriger les joints eau/LoD**, **LoD dans le
brouillard épais** (Nether), rendu, brouillard, SSAO.

### Préréglages serveur (`/voxylod preset <nom>`)

| Préréglage | Rayon LoD | Rayon de génération | Chunks générés en même temps | Lectures disque par tick | Threads | Temps de tick visé |
|---|---|---|---|---|---|---|
| low | 128 | 64 | 2 | 64 | 2 | 35 ms |
| balanced | 256 | 128 | auto | 256 | auto | 40 ms |
| high | 384 | 192 | auto | 384 | auto | 45 ms |
| max | 512 | 256 | 16 | 512 | auto | 48 ms |
| **auto** (par défaut) | low si ≤ 2 cœurs ou < 3 Go de mémoire, high si ≥ 8 cœurs et ≥ 10 Go, balanced sinon. Re-détecté à chaque démarrage | | | | | |
| custom | dès qu'une de ces valeurs est modifiée à la main | | | | | |

« auto » pour les chunks générés en même temps veut dire 2 par thread processeur (de 4 à 32), pour les threads un
tiers des threads du processeur (de 2 à 8).

### Réglages serveur (`config/voxy-server.json`, ou `/voxylod config <réglage> <valeur>` en jeu)

| Réglage | Défaut | Rôle |
|---|---|---|
| `enabled` | `true` | Envoyer les LoD aux joueurs |
| `enabled_on_integrated_server` | `true` | Aussi en solo / LAN |
| `max_radius_chunks` | 256 | Plus grand rayon (en chunks) envoyé à un joueur |
| `max_chunk_reads_per_tick` | 256 | Plafond de lectures disque par tick, baissé tout seul si le serveur lag |
| `max_in_flight_per_player` | 512 | Plafond de demandes en attente par client |
| `max_bytes_per_second_per_player` | 0 | Limite de débit par joueur, 0 = limité seulement par sa connexion |
| `worker_threads` | 0 (auto) | Threads qui lisent et encodent les chunks |
| `target_tick_time_ms` | 40 | Temps de tick au-dessus duquel LoD et génération ralentissent |
| `auto_generate` | `true` | Générer le terrain jamais visité autour des joueurs |
| `auto_generate_radius` | 128 | Rayon de génération (limité aussi par `max_radius_chunks`) |
| `auto_generate_max_concurrent` | 0 (auto) | Chunks générés en même temps |
| `auto_generate_on_integrated_server` | `false` | Générer aussi en solo / LAN |
| `live_updates` | `true` | Envoyer les modifications de blocs aux joueurs qui les voient en LoD |
| `live_update_min_interval_seconds` | 30 | Délai minimum entre deux mises à jour du même chunk |
| `persist_timestamp_cache` | `true` | Garder le cache serveur entre deux redémarrages (`world/data/voxy_lod_cache.bin`) |
| `preset` | `auto` | Voir les préréglages serveur |

Les chunks générés sont sauvegardés comme des chunks normaux : le monde grossit sur le disque au fur et à mesure.

## Commandes

Toutes les commandes `/voxylod` demandent le niveau opérateur 2, ou d'être le propriétaire d'un monde solo / LAN
(pas besoin des cheats).

| Commande | Effet |
|---|---|
| `/voxylod status` | Temps de tick, joueurs, débit, avancement de la génération |
| `/voxylod preset` | Affiche le préréglage actuel |
| `/voxylod preset <auto\|low\|balanced\|high\|max>` | Applique un préréglage (sauvegardé) |
| `/voxylod config` | Liste tous les réglages avec leur valeur |
| `/voxylod config <réglage>` | Affiche un réglage |
| `/voxylod config <réglage> <valeur>` | Change un réglage, appliqué tout de suite et sauvegardé |
| `/voxylod generation pause` / `resume` | Met en pause / relance la génération |
| `/voxylod reload` | Relit `config/voxy-server.json` après une modification à la main |
| `/voxylod cache clear` | Vide le cache du serveur (tout est relu depuis le disque) |

Touches : **F6** active/désactive le rendu Voxy, **F3** affiche une ligne « Server LoD » (débit reçu, demandes en cours, cache).

## Comment ça marche

```
 client                                              serveur
 ──────                                              ───────
 se connecte, annonce les canaux voxy ──────────▶    envoie la version du protocole, le rayon, la limite
 demande les chunks en anneaux autour ──────────▶    pour chaque demande :
 du joueur (avec la date de la copie                   · chargé et modifié     → encodé depuis la mémoire
 qu'il a déjà)                                         · connu comme inchangé  → « inchangé » (pas de lecture disque)
                                                       · sinon lu dans le fichier de région, hors du thread principal
 convertit les sections en LoD        ◀──────────    « données » / « inchangé » / « absent »
 (conversion Voxy, hors du jeu)
                                      ◀──────────    « push » : chunks modifiés par des joueurs, et chunks qui
                                                      ont fini d'être générés après la demande du client
```

- Canaux réseau `voxy:lod_config`, `voxy:lod_request`, `voxy:lod_chunk`, protocole version 2. Les paquets
  contiennent les palettes brutes de blocs et de biomes et la lumière de chaque section, la compression réseau de
  Minecraft s'applique.
- Les chunks dans la distance de vue vanilla sont convertis par Voxy normalement, le serveur n'envoie que ce qui est
  au-delà (plus les 3 anneaux du bord, que Voxy ne peut pas convertir depuis les données vanilla car leurs voisins manquent).
- La génération pose un ticket temporaire sur les chunks jamais générés, laisse Minecraft les générer jusqu'au bout,
  les envoie aux joueurs et garde les plus récents chargés un moment pour que leurs voisins à moitié générés ne
  soient pas sauvegardés puis rechargés en boucle.

## Performances

Mesuré sur un Ryzen 5 9600X (6 cœurs / 12 threads), RTX 5070, SSD NVMe, serveur de test avec 4 Go de mémoire :

| | Résultat |
|---|---|
| Envoi de terrain déjà généré | environ 1200 à 1300 chunks/s à un joueur (limité par la conversion des LoD côté client) |
| Génération en arrière-plan | environ 6000 chunks/min (3× la première version), tick serveur autour de 7 ms, aucun « Can't keep up » |
| Zone neuve après une téléportation | LoD jusqu'à l'horizon dans toutes les directions après environ 90 s |

Ce qui a été mesuré et **n'aide pas** la génération en 1.20.1 (donc non recommandé pour ça) : C2ME, Noisium et
Starlight (la limite vient de la façon dont Minecraft 1.20.1 organise la génération, pas du processeur), et la
génération multi-threads de C2ME (2× plus lente).

## Compatibilité

| Mod | État |
|---|---|
| Sodium 0.5.13 | obligatoire, les autres versions sont refusées exprès |
| Iris 1.7.6 + Complementary Reimagined r5.9.3 | testé, fonctionne |
| Fabric API 0.92.6 | testé |
| FerriteCore 6.0.1, ModernFix 5.25.2, ImmediatelyFast 1.5.5 | testés ensemble, fonctionnent (moins de mémoire, interface plus rapide) |
| Lithium | fonctionne |
| Chunky | fonctionne (les mondes pré-générés sont aussi envoyés) |
| C2ME, Noisium, Starlight | se chargent, aucun gain mesuré avec ce mod |
| Nvidium | **incompatible** : sa version 1.20.1 exige Sodium 0.5.7/0.5.8 |
| Distant Horizons | non testé, ce sont deux moteurs de LoD, n'utilise pas les deux |
| Voxy officiel / autres versions de Voxy | ne pas installer en même temps, ce mod **est** Voxy (id `voxy`) |

## Problèmes fréquents / FAQ

**Le jeu plante avec « class file version 65 »** : le jeu tourne avec un Java trop ancien, choisis Java 21 dans ton launcher.

**« this server uses another version of the mod »** dans le chat : le client et le serveur n'ont pas le même jar.
Utilise la même version des deux côtés.

**Voxy n'affiche rien** : vérifie que Sodium est exactement en 0.5.13 et que Voxy est activé (F6). Les cartes
graphiques AMD très anciennes et Intel ne sont pas supportées par Voxy.

**Du terrain lointain manque juste après être arrivé dans un endroit nouveau** : le serveur est en train de le
générer, il apparaît en 1 à 2 minutes. `/voxylod status` montre l'avancement.

**« Connexion au serveur... » dure plusieurs minutes** : c'est Minecraft qui attend les serveurs de Mojang
(téléchargement de la liste des serveurs bloqués) avant de se connecter, pas ce mod.

**Mon PC rame** : choisis le préréglage Faible ou Moyen. **Le serveur rame** : `/voxylod preset low` ou `/voxylod generation pause`.

**Où sont stockés les LoD ?** Client : `.minecraft/.voxy/` (par adresse de serveur, ou par nom de serveur avec
l'option). Serveur : seulement son fichier de cache `world/data/voxy_lod_cache.bin`.

## Différences avec le Voxy officiel

| | Voxy officiel (Cortex) | Unofficial Backport Voxy |
|---|---|---|
| Version de Minecraft | versions récentes | 1.20.1 Fabric |
| Terrain lointain en multijoueur | seulement ce que tu as exploré | envoyé par le serveur |
| Terrain jamais visité | il faut le pré-générer | généré en arrière-plan par le serveur |
| Constructions des autres joueurs | pas à jour tant que tu n'y vas pas | mises à jour en direct |
| Fonctionne sur un serveur dédié | non (mod client) | oui, même jar |
| Réglages | manuels | préréglages (client et serveur) + manuels + commandes en jeu |
| Joints eau/LoD, brouillard du Nether, stockage par nom de serveur, touche F6 | — | inclus |
| Moteur de rendu, format des LoD, qualité visuelle | — | **identiques** |

## Crédits et licences

| Projet | Auteur | Licence | Utilisé pour |
|---|---|---|---|
| [Voxy](https://github.com/MCRcortex/voxy) | **MCRcortex (Cortex)** | Tous droits réservés | Tout le moteur de rendu, ce dépôt en est un fork GitHub |
| [Portage multiversion de Voxy](https://github.com/m3t4f1v3/voxy) | m3t4f1v3 | (fork de Voxy) | Portage 1.20.1 / Sodium 0.5 sur lequel ce projet repose |
| [voxy-seam-fix](https://modrinth.com/mod/voxy-seam-fix) | HTRMC | MIT | Alignement de la surface de l'eau entre les niveaux de LoD (code adapté, mention dans `voxy-third-party-notices.txt`) |
| [voxy-extra](https://github.com/ImGRUI/voxy-extra) | ImGRUI | LGPL-3.0 | Idées seulement (brouillard du Nether, touche d'activation, stockage par nom de serveur), réécrites, aucun code copié |
| [LOD Server Support](https://github.com/VoX/lod-server-support) | VoX | MIT | Idée seulement (envoyer les chunks une fois générés), aucun code copié |
| Voxygen | — | licence propre (pas de dérivés) | **Non utilisé**, la génération est une implémentation indépendante |
| [Sodium](https://github.com/CaffeineMC/sodium) | CaffeineMC | PolyForm Shield 1.0.0 | Dépendance obligatoire, non incluse |
| [Iris](https://github.com/IrisShaders/Iris) | IrisShaders | LGPL-3.0 | Dépendance optionnelle, non incluse |
| [Fabric API](https://github.com/FabricMC/fabric) | FabricMC | Apache-2.0 | Dépendance obligatoire, non incluse |
| [Complementary Reimagined](https://modrinth.com/shader/complementary-reimagined) | EminGT | Licence Complementary | Pack de shaders utilisé pour les tests, non inclus |

**Voxy est © MCRcortex, tous droits réservés.** Ce dépôt est un fork créé avec la fonction fork de GitHub et publié
pour les personnes qui veulent le compiler et l'utiliser elles-mêmes. **Aucun jar n'est redistribué en release.**
Tous les droits sur Voxy restent à son auteur : si Cortex demande le retrait de ce dépôt, il sera retiré.
Les ajouts de ce fork (LoD serveur, génération, préréglages, commandes) sont fournis tels quels, sans garantie.

LoD serveur et génération écrits par tkettela avec l'aide de Claude (Anthropic).
