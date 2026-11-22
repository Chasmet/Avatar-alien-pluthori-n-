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