> 🇬🇧 [Read in English](README.md)

# DivePilot

**Pilotez votre boîtier étanche HotDive — caméra et ordinateur de plongée — depuis n'importe quel téléphone Android (7+), 100 % hors-ligne, sans compte ni login.**

DivePilot est une application Android (Kotlin + Jetpack Compose) qui **pilote le boîtier étanche HotDive** en Bluetooth et en reprend les fonctions d'**ordinateur de plongée**. Elle permet de **continuer à utiliser votre boîtier HotDive**, même si l'application d'origine n'est plus disponible.

C'est une **réimplémentation** : aucun code de l'application d'origine n'est copié. Seuls les **faits d'interopérabilité** (UUID BLE et octets de commande) sont réutilisés pour pouvoir dialoguer avec le boîtier.

---

## Téléchargement

**Dernière version (v0.9.0) :** [dive-pilot-0.9.0.apk](https://github.com/Soleiado/Divepilot/releases/download/v0.9.0/dive-pilot-0.9.0.apk)

Voir toutes les versions sur la [page Releases](https://github.com/Soleiado/Divepilot/releases).

---

## Points forts

| | |
|---|---|
| 📱 **Tout téléphone Android 7.0+** | minSdk 24 — fonctionne sur les appareils anciens comme récents |
| 🔌 **100 % hors-ligne** | aucune connexion internet, aucun serveur, aucune donnée envoyée |
| 🔐 **Sans compte** | pas de login, pas d'inscription, pas de suivi |
| 🌍 **Bilingue** | français / anglais (Réglages → Langue) |
| 🆓 **Gratuit** | sans publicité |

---

## Fonctionnalités

### 🔗 Connexion au boîtier (Bluetooth Low Energy)
- Scan, appairage, connexion GATT et abonnement aux notifications.
- Reconnexion automatique ; le téléphone ne fait que **recevoir** (aucune écriture).

### 📷 Caméra pilotée par le boîtier
- 4 modes : **Photo**, **Autoportrait**, **Vidéo**, **Vidéo autoportrait**.
- Les boutons du boîtier pilotent : mise au point, zoom, bascule photo/vidéo, déclencheur, menu.
- Réglages caméra : balance des blancs, taille d'image, taille vidéo, exposition.
- Le **flash est désactivé** (la lumière se réfléchit dans le boîtier).

### 🤿 Ordinateur de plongée (MODE TEST)
Désactivé par défaut — à activer dans **Réglages → « Option expérimentale »** (avec un grand avertissement).

- Décompression **Bühlmann ZHL-16C** avec **Gradient Factors 30/70**.
- **NDL** (temps restant sans palier), **paliers de décompression** + **TTS**, plafond.
- **Toxicité oxygène** : CNS % + OTU (nitrox, O₂ ≤ 40 %).
- **Intervalle de surface** et **temps avant avion** (no-fly), plongées répétées.
- **Altitude** (0 / 1000 / 2000 / 3000 m) et **eau douce / salée**.
- **Palier de sécurité** (6 m).

> ⚠️ **Ne pas utiliser comme ordinateur de plongée réel.** Cette fonctionnalité est en **MODE TEST** : les calculs ne sont pas validés et ne remplacent ni une formation ni un ordinateur certifié.

### 📖 Journal de plongée
- Enregistre chaque plongée : **profil de profondeur**, durée, profondeur max, températures, **coordonnées GPS** (dernière position avant immersion), altitude, eau douce/salée.
- Coordonnées GPS **cliquables** → ouverture dans Google Maps.

### 🧭 Écrans annexes
- **Boussole** (capteur du téléphone).
- **Galerie** des photos/vidéos prises.
- **Guide d'utilisation** intégré.
- **Correction colorimétrique** des photos sous-marines.
- **Réglages** : unités (m/ft, °C/°F), langue, alertes, ordinateur de plongée.
- **Service d'avant-plan** : le suivi de plongée continue en arrière-plan.

---

## Fonctionnement (résumé)

1. **Allumez le boîtier**, puis dans DivePilot touchez **« Scanner »** (ou le boîtier dans la liste des appareils appairés).
2. L'app se connecte en **BLE** et écoute les commandes des boutons et la **télémétrie** (batterie, température, profondeur).
3. Depuis l'écran **Caméra**, les boutons du boîtier pilotent la prise de vue ; la télémétrie de plongée s'affiche en surimpression.
4. À plus de 1,2 m, une **plongée démarre** automatiquement (minuteur, profondeur max, palier de sécurité…) et se termine en remontant sous 0,5 m.
5. La plongée est **enregistrée dans le journal** avec son profil, sa position GPS, etc.

---

## Protocole BLE (faits d'interopérabilité)

- **Service** : `e6400001-b5a3-f393-e0a9-e50e24dcca9e`
- **Notification/lecture** : `e6400003-b5a3-f393-e0a9-e50e24dcca9e`
- **Écriture** : `e6400002-b5a3-f393-e0a9-e50e24dcca9e` (non utilisée)
- **Commandes** (2 octets) : `f101` focus · `f102` boussole · `f103` photo/vidéo · `f105`/`f106` zoom avant · `f107`/`f108` menu · `f109`/`f10a` zoom arrière · `f10b` déclencheur · `f10d` flash
- **Télémétrie** (13 octets) : batterie, température (°C), profondeur (m)

---

## Dépendances

### Outils de build

| Composant | Version |
|---|---|
| Gradle | 9.7.1 |
| Android Gradle Plugin (AGP) | 9.4.1 |
| Kotlin | 2.4.21 |
| compileSdk / targetSdk | 37 |
| minSdk | 24 (Android 7.0) |
| JDK | 17+ (build testé avec JDK 25) |

### Bibliothèques

| Bibliothèque | Version | Usage |
|---|---|---|
| Jetpack Compose (BOM) | 2026.08.00 | UI (`ui`, `ui-graphics`, `material3`, `ui-tooling-preview`) |
| AndroidX Core KTX | 1.16.0 | extensions Kotlin |
| AndroidX Lifecycle | 2.9.1 | `runtime-ktx`, `runtime-compose`, `viewmodel-compose` |
| AndroidX Activity Compose | 1.10.1 | intégration Compose |
| AndroidX Navigation Compose | 2.10.2 | navigation entre écrans |
| CameraX | 1.6.1 | caméra (`core`, `camera2`, `lifecycle`, `video`, `view`) |
| Gson | 2.11.0 | sérialisation du journal de plongée |
| JUnit | 4.13.2 | tests unitaires (modèle de décompression) |

---

## Prérequis & Build

- **JDK 17 ou plus**.
- **Android SDK** avec les plateformes `android-37` (et `android-34`).

```powershell
# APK de debug
.\gradlew.bat :app:assembleDebug

# APK de diffusion (signé)
.\gradlew.bat :app:assembleRelease
```

> La signature release utilise `keystore/divepilot-release.jks` (exclu de git — à conserver précieusement pour les futures mises à jour).

---

## Licence & crédits

- Le **code** est sous licence **MIT** — voir [`LICENSE`](LICENSE).
- **Icône** « scuba_diving » : Material Symbols (Google), licence **Apache 2.0**.
- **Image de fond** : « Sea fans coral reef Pagan 2022 », NOAA (domaine public).
- Voir [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) pour le détail.

---

## Avertissement

L'**ordinateur de plongée** est une fonctionnalité **expérimentale, désactivée par défaut**. Ne l'utilisez **jamais** pour planifier ou réaliser une plongée réelle. DivePilot n'est pas un appareil de plongée certifié.
