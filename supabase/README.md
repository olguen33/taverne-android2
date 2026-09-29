# Synchronisation multijoueur

Projet de développement créé le 29 septembre 2026 : `dgvvocfpxflmfqaaakoe` (`eu-west-3`).
Migration `shared_game_initial` appliquée et migration `restrict_profile_trigger` appliquée.
La clé publique et l’URL sont à renseigner dans la configuration Android au moment du raccordement ; aucune clé secrète ne doit être incluse.

`migrations/0001_shared_game.sql` crée le modèle partagé de La Taverne dans un **nouveau** projet Supabase : profils, personnages, contrats, créneaux, votes, messages et catalogue. Les règles RLS limitent les modifications de personnages à leur propriétaire. Les opérations qui touchent plusieurs lignes (achat, inscription et votes, verrouillage, paiement) sont des fonctions SQL atomiques.

## Mode partagé v0.38.0

L’application ouvre désormais la compagnie en ligne par défaut. Les anciens comptes et leurs données restent dans l’interface locale accessible depuis l’écran en ligne. Un joueur crée un compte avec e-mail, mot de passe et pseudo, confirme son adresse si demandé, puis peut importer une fois les personnages de son ancien compte local. Les personnages, inventaires, contrats, créneaux, votes, messages, rumeurs, boutique et paiements de prime du mode en ligne lisent le projet Supabase. Le bouton d’actualisation recharge les changements d’un autre appareil ; revenir à l’application actualise aussi les données.

Les anciens contrats, votes, cartes de donjon et fichiers joints locaux ne sont pas transférés automatiquement. Les fichiers `content://` du téléphone doivent être conservés et téléversés séparément avant d’être accessibles depuis un autre appareil. Le mode local reste conservé pour cet historique. L’app ne supprime aucune donnée locale après import.

## Vérifications avant diffusion

1. Les migrations `0001` à `0004` sont appliquées au projet `dgvvocfpxflmfqaaakoe`. Vérifier les politiques avec deux comptes distincts et les tests de CI.
2. La clé **publishable** figure dans l’APK, jamais une clé secrète ou `service_role`.
3. Vérifier sur deux appareils la connexion, l’import, les votes, la date MJ, l’achat et le paiement unique. La session est restaurée avec un jeton renouvelable.
4. Ne publier l’APK signé qu’après compilation et vérification de sa signature avec celle de la v0.37.1.

La v0.37.1 déjà installée reste entièrement locale ; seul le nouvel APK propose le mode partagé.
