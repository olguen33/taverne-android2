# La Taverne sur iPhone

Client iOS natif SwiftUI pour la compagnie en ligne. La session est conservée dans le trousseau iOS. Les contrats et personnages utilisent le même projet Supabase que l'application Android.

## Compilation

Sur macOS avec Xcode et XcodeGen :

```sh
xcodegen generate --spec ios/project.yml --project ios
xcodebuild -project ios/LaTaverne.xcodeproj -scheme LaTaverne -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

Le workflow GitHub Actions compile pour le simulateur. Ce résultat ne s'installe pas sur un iPhone réel. Pour TestFlight, rattacher `fr.taverne.mercenaires.ios` à l'équipe Apple Developer, activer la signature dans Xcode, archiver pour iOS, envoyer à App Store Connect puis inviter les testeurs externes. Aucun certificat ni identifiant Apple n'est conservé dans ce dépôt.

La connexion Internet est nécessaire. Les fichiers locaux et les cartes de donjon stockés uniquement sur Android ne sont pas transférés. Les autres fonctions partagées sont développées dans les écrans SwiftUI du dossier `LaTaverne`.
