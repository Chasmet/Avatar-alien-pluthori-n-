# CHK Alien Live — Avatar Alien Pluthorien

**Dépôt officiel exclusif :** https://github.com/Chasmet/Avatar-alien-pluthori-n-

Application Android Java avec compileSdk/targetSdk 34, minSdk 21 (compat) ou 23 (Honor).

## Fonctionnalités intégrées

- Caméra frontale, suivi du visage via ML Kit Face Detection embarqué pour Honor 200 ; mode Android classique disponible pour appareils plus anciens
- Huit styles de voix, préécoute micro en direct, réglages de profondeur et de mix
- Décors évolutifs animés : nuages, particules, pluie, tempêtes, éclairs et météo
- Plusieurs personnages, choix des décors et importation locale depuis la galerie
- Mode plein écran destiné au partage d'écran TikTok LIVE mobile
- Vérification de nouvelles versions via le manifeste update.json de ce dépôt

**Limites réelles :** les avatars actuels sont des portraits 2D, et non des modèles faciaux photoréalistes riggés ; la bouche reste une simulation. Android ne garantit pas la capture du son transformé par TikTok. Ces fonctions doivent être testées sur le téléphone.

## Compilation

Workflow officiel : `.github/workflows/android.yml`. Commande : `bash ./gradlew assembleHonorDebug assembleHonorRelease assembleCompatDebug`. Le workflow publie des artefacts de test et un APK release non signé.

## MAJAUTO

Le package stable est `fr.chk.alienlive`. Les versions stables doivent être signées avec la même clé permanente que la v0.5.11, qui ne doit jamais être committée.

Après compilation, l'APK est signé hors dépôt, transféré dans `distribution/`, et accompagné de `update.json` avec versionCode, versionName, packageName, apkUrl et SHA-256. Le workflow `.github/workflows/publish.yml` contrôle les métadonnées et publie la Release.

Le téléphone consulte `https://raw.githubusercontent.com/Chasmet/Avatar-alien-pluthori-n-/main/update.json`, télécharge l'APK vérifié puis demande à Android de procéder à l'installation.

**Migration depuis v0.5.11 :** cette ancienne version pointe encore vers l'ancien dépôt. Installer une fois la nouvelle version depuis ce dépôt **par-dessus** v0.5.11, sans la désinstaller, dès lors que la signature est identique. Les futures versions utiliseront le nouveau canal.

Le projet reste gratuit et ne nécessite aucun serveur de calcul ni API payante.