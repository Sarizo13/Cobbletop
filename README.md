# CobbleTop (v1.4.0)

CobbleTop ajoute des placeholders TextPlaceholderAPI pour afficher des tops (Top 10 Shinies / Top 10 Dex) basés sur les stats du monde. [file:583]

## Installation
- Installer Fabric + Fabric API
- Installer Text Placeholder API (pb4)
- Installer LuckPerms (optionnel mais recommandé pour les préfix)
- Mettre le jar CobbleTop dans `/mods`
- Redémarrer le serveur

## Configuration
Un fichier est créé automatiquement au premier lancement : [file:583]

`config/cobbletop/cobbletop.yml` [file:583]

Options utiles :
- `refreshSeconds`: fréquence de recalcul (min 5s)
- `nameColWidth`: largeur d’alignement de la colonne pseudo
- `titleShinies`, `titleDex`: titres des panneaux
- `lineFormatShinies`, `lineFormatDex`: format des lignes top10
- `meLineFormatShinies`, `meLineFormatDex`: format de la ligne “toi”
- `emptyLineFormat`: format des lignes vides
- `footerSeparator`: ligne de séparation avant la section “toi”
- `footerLabel`: texte affiché juste avant la ligne “toi” (ex: “Dernière ligne : toi”)

Tokens disponibles (formats de lignes) :
- `{badge}`: badge (★/✦/✧/vide)
- `{rank}`: rang formaté (#01..#10)
- `{name}`: nom du joueur (avec prefix LuckPerms si dispo)
- `{namePadded}`: nom du joueur aligné avec `nameColWidth`
- `{value}`: valeur (shinies/dex)
- `{lead}`: préfixe de la ligne “toi” (★ ou …)
- `{pos}`: position du joueur (toi)

## Placeholders
CobbleTop expose uniquement 2 placeholders “tout-en-un” (multi-lignes via `\n`) : [file:583]

- `%cobbletop:shinies_all%` : titre + top 10 shinies + séparation + label + ta ligne (“toi”) [file:583]
- `%cobbletop:dex_all%` : titre + top 10 dex + séparation + label + ta ligne (“toi”) [file:583]

Note : la dernière ligne correspond à ton joueur (si le placeholder est évalué dans un contexte joueur). [file:583]

## Commande
- `/cobbletop reload` : recharge `config/cobbletop/cobbletop.yml` [file:583]

## Exemple (HoloDisplays)
Tu peux afficher le panneau complet (multi-lignes) dans un hologramme HoloDisplays : [file:583]

- Shinies : `%cobbletop:shinies_all%` [file:583]
- Dex : `%cobbletop:dex_all%` [file:583]

Exemple (extrait) :
text: "%cobbletop:shinies_all%" [file:583]

