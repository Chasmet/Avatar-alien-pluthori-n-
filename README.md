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
## Prototype GLB natif

Le Pluthorien Alpha inclut maintenant le véritable fichier `app/src/main/assets/pluthorien_alpha.glb`, chargé sur Android par `Alien3DView` (OpenGL ES 2.0 local). Il tourne dans le décor animé ; la mâchoire `Jaw_Lower_Animatable` se déplace avec la détection faciale. Dans Réglages, désactive `Pluthorien Alpha — véritable modèle GLB 3D` pour revenir au portrait 2D. Les autres extraterrestres restent en 2D. Ce premier GLB dérivé d'une seule image conserve une texture frontale réaliste mais ses profils sont encore approximatifs : aucun modèle facial phonémique complet n'a été créé.

## Audit 22 novembre 2026

L'interface utilisateur devient « Nous sommes le peuple de Pluthor » ; la pastille TikTok est supprimée. Le suivi caméra et l'animation de la mâchoire ont été affinés ; parallaxe, brume et déplacements du décor sont renforcés. Voir `AUDIT-2026-11-22.md` pour les limites physiques des lèvres du modèle GLB. Le nouveau workflow `automatic-release.yml` publie uniquement si la clé de signature permanente est présente dans les secrets GitHub et qu'elle correspond exactement au certificat historique.

## Les cinq modèles GLB (mise à jour du 22 novembre 2026)

Les cinq modèles prédéfinis sont inclus dans `app/src/main/assets/` : `pluthorien_alpha_rig.glb`, `pluthorien_sage_rig.glb`, `pluthorien_guard_rig.glb`, `pluthorien_shadow_rig.glb` et `pluthorien_annunaki_rig.glb`. Les modèles ont été générés à partir des portraits originaux de l'application, avec tête texturée, buste et partie inférieure de mâchoire distincte `Jaw_Lower_Animatable`. Le GLB Alpha historique reste disponible avec l'option « modèle 3D d'origine » dans les réglages. Les fichiers utilisent glTF 2.0 avec `EXT_texture_webp` et textures embarquées ; le rendu Android local les lit via BitmapFactory, sans serveur de génération. Le personnage personnalisé importé reste en 2D. **Limite** : la géométrie reconstruite d'une seule photo ne possède pas tous les blendshapes nécessaires au lip-sync phonémique, et les profils sont approximatifs.

## Préversion Android installable (canal parallèle)

En l'absence de la **clé privée de signature historique**, le dépôt publie automatiquement sur GitHub Releases, après une compilation réussie, les APK de prévisualisation `CHK-Pluthor-Honor-Preview.apk` et `CHK-Pluthor-Android-Preview.apk`. Ils portent volontairement le package `fr.chk.alienlive.debug` pour s'installer **en parallèle** de l'application stable : l'ancienne application et ses données restent intactes. Le mode debug utilise `update-preview.json`, distinct de `update.json`, pour proposer ses futures mises à jour dans l'application. La clé debug est conservée dans un cache GitHub Actions (ce cache est révocable/expirable, il ne remplace pas le stockage permanent d'une clé de production). Cette préversion ne doit pas être présentée comme une mise à jour stable de `fr.chk.alienlive`. Une MAJAUTO stable sans réinstallation n'est cryptographiquement possible qu'avec le certificat privé historique.

Les APK de prévisualisation portent les noms de Release `app-honor-debug.apk` et `app-compat-debug.apk`; le manifeste `update-preview.json` doit pointer exactement vers le premier fichier. La publication est maintenant dans le workflow de compilation sur branche main.\n
### Signature vérifiée de la préversion v2

À partir du canal `update-preview-v2.json`, l'application de prévisualisation porte le package `fr.chk.alienlive.preview` et utilise un certificat de débogage distinct de la v0.6.16 (`fr.chk.alienlive.debug`). Le workflow crée une clé JKS dédiée **une seule fois**, la réutilise à chaque compilation et refuse de régénérer un certificat différent si le manifeste v2 existe. Le cache GitHub Actions n'étant pas un coffre de clés permanent, des secrets ou une sauvegarde privée du JKS restent nécessaires pour garantir les mises à jour à très long terme. Le canal historique `fr.chk.alienlive` ne peut être mis à jour sans retrouver son certificat privé d'origine.

### Lecture des versions sans manifeste GitHub à écrire

Le canal de prévisualisation v2 lit désormais directement les Releases GitHub publiques (uniquement les tags `chk-pluthor-preview-v2-*`) et vérifie le SHA-256 fourni avec l'APK. Il n'exige plus qu'un workflow modifie un fichier manifeste via l'API GitHub. Le fichier `update-preview-v2.json` conservé dans le dépôt est un **marqueur public de certificat** : si le cache de la clé privée est perdu, les nouvelles compilations sont bloquées, ce qui empêche la publication d'une mise à jour impossible à installer sur la version existante.
