# La Taverne sur iPhone

Client iOS SwiftUI pour la compagnie en ligne. Il ouvre le site de jeu hébergé et conserve la session dans le stockage du navigateur intégré. Les contrats et personnages restent dans le même projet Supabase que l'application Android.

## Compilation

Sur macOS avec Xcode et XcodeGen :

```sh
xcodegen generate --spec ios/project.yml --project ios
xcodebuild -project ios/LaTaverne.xcodeproj -scheme LaTaverne -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

Le workflow GitHub Actions compile pour le simulateur. Ce résultat ne s'installe pas sur un iPhone réel. Pour TestFlight, rattacher `fr.taverne.mercenaires.ios` à l'équipe Apple Developer, activer la signature dans Xcode, archiver pour iOS, envoyer à App Store Connect puis inviter les testeurs externes. Aucun certificat ni identifiant Apple n'est conservé dans ce dépôt.

La connexion Internet est nécessaire. L'application reprend l'interface et les fonctions actuellement disponibles dans la version web ; les fonctions conservées uniquement sur le téléphone Android ne sont pas transférées.