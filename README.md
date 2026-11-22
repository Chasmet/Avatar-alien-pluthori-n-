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

## Fidélité du rendu (correctif 22 novembre 2026)

**La version stable v0.6.9 est la référence visuelle.** Sans action de l'utilisateur :
- Le personnage Alpha utilise `pluthorien_alpha.glb` (fichier original identique, SHA Git `bf81d4ec17731d4535ab0bfa7d91feda9976caee`) et son pivot de mâchoire d'origine.
- Les personnages Sage, Gardien, Ombre et Annunaki utilisent leurs portraits photoréalistes historiques, non les GLB approximatifs.
- Le cadrage du décor suit les mêmes proportions que la stable.
- Les cinq fichiers `*_rig.glb` restent disponibles, mais leur utilisation exige d'activer explicitement « Tester les GLB reconstruits des autres aliens » ou « Tester le GLB Alpha reconstruit » dans Réglages.
- L'ancienne préférence `originalAlphaModel=false` (héritée d'une préversion) ne force plus le visage reconstruit.
- `tools/verify_visual_fidelity.py` bloque une compilation si le GLB original est altéré ou si ces réglages deviennent actifs par défaut.

Cette correction est un retour à la référence d'apparence, pas une validation photographique de chaque écran : tester sur Honor 200 avant de diffuser.


## Améliorations LIVE — lèvres, voix et GLB individuels (22 novembre 2026)

- La bouche 3D réagit à la détection ML Kit **et** au niveau de parole réel du micro (si le moteur vocal est activé). Sans son, aucune animation artificielle ; le mode photo 2D conserve aussi ses animations d'origine.
- Le moteur de voix locale traite maintenant le signal à 44,1 kHz, sans allouer de nouveau tableau de presets à chaque échantillon ; ajout de filtrage passe-haut, réduction légère du bruit et lissage du niveau servant aux lèvres. Les réglages de voix existants restent disponibles.
- Les cinq GLB embarqués demeurent : Alpha original par défaut ; Sage, Gardien, Ombre et Annunaki activables **un par un** depuis Personnages sans remplacer les portraits 2D par défaut. Le réglage global de test 3D demeure accessible.
- MAJAUTO : la Release contient les sommes SHA-256 pour les APK Honor et Compat. Le gestionnaire exploite le digest GitHub ou, en secours, les sommes inscrites dans les notes, puis vérifie le certificat Android avant l'installation. La même signature est obligatoire pour conserver les données.
- Limites : pas de synchronisation phonétique complète sans blendshapes faciaux ; une application externe ne reçoit pas automatiquement la voix transformée par CHK. Tests visuels et audio nécessaires sur appareil réel.

## Cinq avatars GLB HD articulés (génération déterministe)

La compilation génère cinq fichiers GLB **réels** dans `app/src/main/assets/` :

| Personnage | Fichier facultatif |
| --- | --- |
| Pluthorien Alpha | `pluthorien_alpha_hd.glb` |
| Pluthorien Sage | `pluthorien_sage_hd.glb` |
| Pluthorien Gardien | `pluthorien_guard_hd.glb` |
| Pluthorien Ombre | `pluthorien_shadow_hd.glb` |
| Annunaki ancien | `pluthorien_annunaki_hd.glb` |

- `python3 tools/generate_alien_hd.py` réutilise **exactement** les textures WebP embarquées dans les GLB précédents, sans télécharger ni inventer de nouvelles photos.
- Le maillage avant est subdivisé et conserve le portrait d'origine. Le maillage arrière possède une profondeur arrondie, avec un buste et une cavité buccale. Les oreilles des personnages Sage et Gardien sont sculptées en volume.
- Les cibles de déformation `mouthOpen`, `mouthFunnel`, `mouthSmile`, `eyeBlink`, `jawLeft` et `jawRight` sont présentes dans les GLB. Le rendu Android pilote réellement `mouthOpen` et `eyeBlink` par le microphone/la caméra. Les autres cibles sont disponibles pour des moteurs 3D compatibles ; elles ne constituent pas une reconnaissance phonétique.
- `python3 tools/verify_glb_hd.py` contrôle les cinq fichiers, leurs sommets, les articulations et l'identité binaire des textures d'origine. GitHub Actions enregistre les vrais `.glb` après validation puis les place dans l'APK signé.
- Le Pluthorien **Alpha original** `pluthorien_alpha.glb` reste le rendu utilisé par défaut. Les quatre autres portraits 2D historiques restent par défaut ; chaque GLB HD s'active individuellement depuis « Personnages », ou via les réglages expérimentaux.

**Limite importante :** une photographie unique ne donne ni profondeur réelle derrière le visage ni capture des expressions. Il s'agit d'une reconstruction 3D procédurale avec face fidèle de face et arrière approximatif, **pas** d'un scan photoréaliste parfait à 360°. Les visèmes sont une animation géométrique guidée par l'ouverture de la bouche, pas une synchronisation phonème par phonème.
