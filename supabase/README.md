# Synchronisation multijoueur

Projet de développement créé le 29 septembre 2026 : `dgvvocfpxflmfqaaakoe` (`eu-west-3`).
Migration `shared_game_initial` appliquée et migration `restrict_profile_trigger` appliquée.
La clé publique et l’URL sont à renseigner dans la configuration Android au moment du raccordement ; aucune clé secrète ne doit être incluse.

`migrations/0001_shared_game.sql` crée le modèle partagé de La Taverne dans un **nouveau** projet Supabase : profils, personnages, contrats, créneaux, votes, messages et catalogue. Les règles RLS limitent les modifications de personnages à leur propriétaire. Les opérations qui touchent plusieurs lignes (achat, inscription et votes, verrouillage, paiement) sont des fonctions SQL atomiques.

## Raccordement à effectuer

1. Créer un projet Supabase dans une région adaptée aux joueurs et appliquer la migration sur ce projet vide. Vérifier les fonctions et les politiques sur deux comptes de test avant toute donnée réelle.
2. Activer la connexion e-mail et mot de passe. Copier **seulement** l’URL du projet et la clé **publishable** dans la configuration Android ; ne jamais placer une clé secrète ou `service_role` dans l’APK.
3. Dans l’application, demander au joueur de se connecter à son ancien compte local, puis à son nouveau compte en ligne. Montrer l’aperçu des personnages à transférer avant d’appeler `import_local_characters`. La fonction n’accepte qu’un import par compte en ligne. Ne supprimer les données locales qu’après vérification et accord explicite.
4. Les fichiers joints au personnage sont encore des URI du téléphone ; il faudra les transférer séparément dans un espace de stockage avec des droits par propriétaire. Les contrats et votes locaux anciens demandent une migration coordonnée : leurs identifiants de joueurs ne correspondent pas encore aux identifiants en ligne. Ne pas prétendre qu’ils ont été copiés tant que tous les participants n’ont pas lié leurs comptes.
5. Remplacer progressivement les lectures et écritures SQLite de l’interface par les appels de `SupabaseGateway`, gérer les jetons de session et les erreurs hors ligne, puis tester deux téléphones simultanément.

Cette étape prépare les sources. Elle **n’active pas encore** le mode multijoueur dans l’APK actuel : il faut raccorder un projet Supabase et terminer l’interface et la migration de données.
