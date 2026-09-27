# La Taverne — prototype Android natif

Ce projet contient une application Android native en Java. L'interface est composée de vues Android, avec une base SQLite locale. Il n'y a ni WebView, ni page web intégrée.

## Écrans présents

- Tableau des contrats et détail des missions.
- Création et édition de plusieurs personnages, avec fiche, lore et document PDF/image joint.
- Inscription à un contrat avec un personnage, messages et proposition de date.
- Espace MJ accessible à chaque compte : publication et suivi des contrats, avec le pseudo du proposant.
- Disponibilités du proposant par jour de la semaine et plage horaire, sans date de calendrier.
- Écran d’accueil avec création de compte par pseudo et mot de passe, connexion et déconnexion.
- Fiches et lore associés au compte local de chaque joueur.

La version 0.3 s'installe à côté des deux premières applications de test. Leurs données y restent, sans transfert automatique. Cette version utilise une signature de prototype fixe afin que ses prochaines versions puissent s'installer par-dessus elle et conserver ses données. La clé reste hors du dépôt GitHub ; le workflow produit un APK non signé, puis la signature est appliquée séparément.

L'application vérifie une mise à jour sur `https://raw.githubusercontent.com/olguen33/taverne-android2/main/latest.json`. Le dépôt public de distribution doit contenir `latest.json` et `La-Taverne.apk`. Le manifeste contient `versionCode`, `apkUrl` et `sha256`. L'application télécharge l'APK, vérifie son empreinte, puis demande à Android de l'installer. Android requiert une confirmation de l'utilisateur. La clé de signature de prototype conservée dans le dépôt privé Android n'est pas adaptée à une diffusion publique de production.

## État actuel

Les données de ce prototype sont enregistrées **sur le téléphone seulement**. La version web existante utilise un site privé et une connexion ChatGPT qui ne peuvent pas être réutilisés directement par une application Android native. Les contrats et personnages déjà créés sur le site ne sont donc pas encore visibles dans ce prototype. Pour l'utiliser avec plusieurs joueurs, il faut ajouter une API mobile et une authentification adaptées, puis connecter les écrans à cette API.

Ce dossier n'est **pas un APK**. Le présent environnement ne dispose pas du SDK Android, des outils Gradle et des dépendances nécessaires pour compiler et tester un fichier installable.

## Ouvrir et compiler

1. Ouvrir ce dossier dans Android Studio avec JDK 17.
2. Installer la plateforme Android API 37 et les Build Tools proposés par Android Studio.
3. Utiliser Gradle 9.6. Le dossier contient la configuration du wrapper, mais pas le binaire `gradle-wrapper.jar`. Si Android Studio ne configure pas Gradle automatiquement, exécuter `gradle wrapper --gradle-version 9.6` avec une installation locale de Gradle.
4. Synchroniser, puis lancer l'application sur un appareil Android ou générer un APK de débogage.

La compilation n'a pas pu être exécutée ici. Les fichiers XML ont été vérifiés et le code Java a été analysé syntaxiquement, sans accès aux classes du SDK Android.

## Compilation automatisée

Le dossier `.github/workflows/android.yml` prépare une compilation de débogage dans GitHub Actions. Il faut placer ce projet à la racine d’un dépôt GitHub, puis lancer le workflow « Android debug APK ». La compilation et le résultat n’ont pas été vérifiés dans le présent environnement.
