# Changelog - v1.4.1

## ✨ Nouvelles fonctionnalités
- **Auto-refresh automatique** : Le cache se met à jour automatiquement toutes les 10s sans besoin de `/cobbletop reload`
- **Affichage des joueurs à 0** : Les joueurs avec 0 shinies/dex apparaissent maintenant dans la ligne "Toi" avec leur vraie valeur
- **Header separator** : Ligne de séparation ajoutée entre le titre et le top 10
- **Footer label** : Texte configurable avant la ligne "Toi" (ex: "Dernière ligne : toi")

## 🐛 Corrections
- **Titre en double** : Suppression du titre dupliqué dans `allBoard()`
- **Newline fix** : Correction des sauts de ligne (`\n` au lieu de `\\n`)
- **Joueurs à 0 inclus** : Maintenant inclus dans `allShinies` et `allDex` pour affichage correct dans la ligne "Toi"

## 🔧 Amélioration technique
- **ServerTickEvents hook** : Utilise l'event Fabric `END_SERVER_TICK` pour déclencher les refreshs automatiques
- **Interval configurable** : `AUTO_REFRESH_EVERY_MS = 10_000` (modifiable en constant)
- **Thread-safe** : Utilise `AtomicLong` pour le dernier auto-refresh

## 📝 Configuration
Nouvelle option dans `cobbletop.yml` :
```yaml
headerSeparator: "──────────────────────"
footerSeparator: "──────────────────────"
footerLabel: "Dernière ligne : toi"
