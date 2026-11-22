# CHK Alien Live — procédure MAJAUTO

Dépôt et publication exclusivement dans `Chasmet/Avatar-alien-pluthori-n-`.

1. Compiler Honor via `.github/workflows/android.yml` (Android 34, Java 17, Gradle 8.7).
2. Signer l'APK de release avec la **clé JKS permanente de v0.5.11** hors du dépôt public.
3. Placer `CHK-Alien-Live-vX.Y.Z.apk` sous `distribution/` et compléter `update.json` à la racine.
4. `publish.yml` contrôle la signature, le package `fr.chk.alienlive`, la version et SHA-256, puis crée la Release GitHub `chk-alien-live-vX.Y.Z`.
5. L'APK consulte `https://raw.githubusercontent.com/Chasmet/Avatar-alien-pluthori-n-/main/update.json` et propose l'installation Android, sans désinstaller la version stable.

Obligatoire : signature identique, versionCode croissant, même identifiant de package. Ne jamais placer la clé ou ses mots de passe dans un dépôt public.

L'APK v0.5.11 a encore l'ancien canal d'actualisation : l'installation initiale de la nouvelle version issue du dépôt dédié doit être réalisée depuis son APK direct. Les versions suivantes ont MAJAUTO depuis le dépôt dédié.

Si les secrets de signature GitHub Actions ne sont pas configurés, la clé reste privée et l'APK est signé en dehors de CI. La diffusion d'une version ne doit pas être annoncée sans Release effectivement publiée.
## Publication automatisée additionnelle (novembre 2026)

Après succès du workflow Android sur main, `.github/workflows/automatic-release.yml` peut signer l'artefact Honor, comparer son certificat SHA-256 à la Release signée v0.6.9, publier un APK dans une nouvelle Release et mettre à jour `update.json` automatiquement. Les secrets requis sont `APK_KEYSTORE_BASE64` (base64 du JKS permanent v0.5.11), `APK_KEYSTORE_PASSWORD`, `APK_KEY_ALIAS`, `APK_KEY_PASSWORD`. Le workflow ne publie **pas** si ces secrets manquent ou si la signature diffère. Le workflow `publish.yml` historique reste disponible pour le canal de reconstruction des APK déjà signés. Aucun APK non signé ne doit être annoncé comme mise à jour installable.

## Canal preview (solution immédiate indépendante)

Quand la clé stable d'origine est indisponible, `.github/workflows/android.yml` publie une véritable APK debug séparée (applicationId `fr.chk.alienlive.debug`) et un manifeste `update-preview.json`. La même APK préversion recherche ce manifeste et installe les versions suivantes **si la clé DEBUG mise en cache GitHub n'a pas expiré**. Cette clé est réservée aux tests ; la version principale ne change pas de signature et conserve ses données. Le vrai canal stable reste `update.json` et exige toujours le JKS historique. Aucun contournement de signature n'est utilisé.

### Canal preview v2

Le canal `fr.chk.alienlive.preview` utilise `update-preview-v2.json` et la clé `.preview-signing/debug.jks` générée une seule fois puis conservée dans le cache GitHub Actions (clé de cache `chk-pluthor-preview-signature-v2`). Les compilations refusent de recréer une clé si le cache a expiré et que le manifeste existe. Aucun changement de certificat silencieux. La préversion v2 se distingue de l'ancien paquet expérimental `fr.chk.alienlive.debug` ; installer la préversion v2 ne désinstalle pas l'application stable.

### Mise à jour preview v2 via GitHub Releases

L'application vérifie les tags `chk-pluthor-preview-v2-*` par GET public sur l'API GitHub et lit `assets[].digest` SHA-256 avant d'installer. Une autorisation GitHub Contents write n'est donc plus nécessaire pour publier un manifeste. Le workflow publie uniquement les APK issus du build réussi. Une fois la première version v2 publiée et sa clé conservée en cache, le marqueur de certificat `update-preview-v2.json` est ajouté pour empêcher toute régénération de clé privée si le cache expire.
