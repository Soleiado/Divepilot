# DivePilot — Spécification

Réimplémentation (sans copie de code) d'une application Android pilotant la caméra
d'un téléphone via un boîtier étanche en Bluetooth, et reprenant les fonctionnalités
d'ordinateur de plongée de l'application d'origine (« HotDive »).

## 1. Décisions produit

| Sujet | Décision |
|---|---|
| Périmètre | Application complète (caméra + ordinateur de plongée + écrans annexes) |
| Stack | Kotlin + Jetpack Compose |
| minSdk / cible | minSdk 24 (Android 7.0) |
| Communication BLE | Réception seule (le boîtier envoie, le téléphone n'écrit pas) |
| Langues | Français + Anglais |
| Nom affiché / launcher | DivePilot |
| applicationId | `com.divepilot.app` |
| Emplacement | `D:\pascal\Project\DivePilot` |
| Serveur | 100 % local / hors-ligne (pas de compte, pas d'upload) |
| Algorithmes plongée | Fidèles à l'original (seuils et calculs repris) |
| Protocole | UUID BLE et octets de commande réutilisés comme faits d'interopérabilité |
| Validation | Outil de capture des trames BLE brutes d'abord |

## 2. Protocole BLE (faits de compatibilité)

- Filtre de scan : nom d'appareil contenant `dive` (reconnexion : nom contenant `hotdive`).
- Service : `e6400001-b5a3-f393-e0a9-e50e24dcca9e`
- Caractéristique de notification / lecture : `e6400003-b5a3-f393-e0a9-e50e24dcca9e`
- Caractéristique d'écriture : `e6400002-b5a3-f393-e0a9-e50e24dcca9e` (non utilisée, réception seule)
- Descripteur CCCD standard : `00002902-0000-1000-8000-00805f9b34fb`
- Appairage : `createBond()` avant connexion GATT.

### 2.1 Commandes caméra (trames de 2 octets = 4 caractères hexa)

| Commande | Action |
|---|---|
| `f101` | Mise au point (autofocus) |
| `f102` | Boussole (appui long sur la jauge) |
| `f103` | Bascule Photo / Vidéo |
| `f10b` | Déclencheur (photo, ou démarrage/arrêt vidéo) ; ouvre l'appli caméra si absente |
| `f105` / `f106` | Zoom avant |
| `f107` / `f108` | Menu / réglages |
| `f109` / `f10a` | Zoom arrière |
| `f10d` | Flash (bouton présent sur le boîtier, absent de l'appli d'origine) |

Codes **confirmés sur le boîtier réel** (test utilisateur) : `f101` (focus),
`f103` (photo/vidéo), `f10b` (déclencheur), `f107` (menu), `f109` (zoom arrière),
`f10d` (flash). Zoom avant = `f105`/`f106` (reste à confirmer).

### 2.2 Télémétrie (trames de 26 caractères hexa = 13 octets)

| Champ | Position | Décodage |
|---|---|---|
| Batterie | octet 2 (car. 4–5) | hexa → int ; si ≤ 98 → %, sinon 100 |
| Température | octets 8–9 (car. 16–19) | hexa → int, ÷ 100 → °C |
| Profondeur | octets 10–12 (car. 20–25) | `raw = hexa → int` ; `depth_m = round(raw/100 − 100) / 10` |

- Sentinelles : profondeur ≈ 383,2 m ou > 200 m ou < 0 → 0 (transducteur hors d'eau / erreur).
- À confirmer sur le boîtier réel via l'outil de capture.

## 3. Fonctionnalités

### Connectivité
- Scan BLE (filtre `dive`), appairage, connexion GATT, abonnement notification, reconnexion auto.
- Capture des trames brutes (écran dédié / logs hexa) pour validation.

### Caméra (CameraX)
- Photo (JPEG vers MediaStore) et vidéo (MP4 vers MediaStore, avec audio).
- Zoom (linéaire), mise au point, balance des blancs, taille image/vidéo, retardateur, bascule avant/arrière.
- Pilotage par les boutons du boîtier (mapping §2.1).

### Ordinateur de plongée
- Détection début (profondeur > 1,2 m) / fin (≤ 0,5 m) de plongée.
- Profondeur max, température min, durée, journal (liste + détail + graphique).
- Modes SCUBA / Free avec réglages : profondeur max, temps max, vitesse de remontée,
  5 alertes de profondeur (Free), intervalle de surface.
- Palier de sécurité (6 m / 10 m), alertes par vibration + notification.

### Écrans annexes
- Boussole (capteur du téléphone), accueil + vidéo d'intro, guide d'utilisation,
  réglages (unités m/ft, °C/°F, langue), galerie/aperçu médias, correction colorimétrique.

## 4. Architecture proposée

- Monolithe mono-module `app`, Kotlin + Compose.
- Couches :
  - `ble/` — gestionnaire BLE (scan/connexion/notification) + décodeur de protocole.
  - `camera/` — wrapper CameraX (photo/vidéo/zoom/focus).
  - `dive/` — machine à états plongée + alertes (logique pure, testable).
  - `data/` — Room (journal de plongée) + DataStore (réglages).
  - `ui/` — écrans Compose + navigation.
- BLE : BluetoothGatt plateforme enveloppé en coroutines/Flow (ou nRF Connect BLE si besoin).

## 5. Roadmap

1. Scaffold du projet + SPEC + outil de capture des trames BLE brutes.
2. BLE : scan / appairage / connexion / notification + décodage télémétrie + commandes.
3. Caméra CameraX + pilotage par le boîtier.
4. Ordinateur de plongée : état, alertes, journal, réglages de modes.
5. Écrans annexes (boussole, accueil, réglages, galerie, guide).
6. i18n FR/EN, polish, signature, tests.
