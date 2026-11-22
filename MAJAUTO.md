# CHK Alien Live — majauto

## Canal de publication

APK signé et manifeste statique :
- Workflow : `.github/workflows/chk-alien-live-build.yml` sur la branche `chk-alien-live-build-20261121`
- Manifeste : `chk-alien-live/update.json` (créé à la première Release signée)
- Releases : tags `chk-alien-live-v0.5.N` uniquement.

## Première activation obligatoire

Dans GitHub `Settings > Secrets and variables > Actions`, ajouter **quatre secrets** :

- `CHK_ALIEN_KEYSTORE_BASE64` : base64 de la clé privée JKS permanente ;
- `CHK_ALIEN_KEYSTORE_PASSWORD` ;
- `CHK_ALIEN_KEY_ALIAS` ;
- `CHK_ALIEN_KEY_PASSWORD`.

**Ne jamais publier la clé privée, son base64 ni ses mots de passe dans GitHub.** Conserver une sauvegarde indépendante.

Relancer le workflow ou effectuer un nouveau commit sur la branche. GitHub Actions compile `assembleDebug`, puis `assembleRelease`, vérifie package et signature et publie la Release seulement en cas de succès. Le manifeste contient `versionCode`, `versionName`, `packageName`, `apkUrl`, `sha256`.

## Conservation des données

Les anciens APK v0.3 / v0.4 sont installés sous le package `fr.chk.alienlive.debug` et signés par des clés de débogage éphémères. Ils ne sont **pas compatibles** avec l'APK stable signé sous `fr.chk.alienlive`. La transition initiale nécessite une nouvelle installation (les deux versions peuvent coexister). **Après cette transition**, ne changer ni l'identifiant de package ni la clé privée pour préserver les données lors des mises à jour.

Le téléchargement et l'installation sont soumis à une confirmation de l'utilisateur par Android ; ne pas prétendre que l'APK peut être installé silencieusement.
