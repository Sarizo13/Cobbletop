# CobbleTop v1.4.1 Release

## 🎯 What's New

### ✨ Features
- **Auto-refresh every 30 seconds**: Cache updates automatically without manual `/cobbletop reload`
- **Force save player stats**: Player statistics are saved to disk immediately on each refresh cycle
- **Accurate DEX calculation**: Now includes `captured + evolved + shinies + 1 (starter)` instead of vanilla stat
- **Players with 0 stats visible**: Shows your stats in "Toi" line even if you have 0 shinies/dex
- **Header & footer separators**: Configurable visual separators for better formatting

### 🐛 Bug Fixes
- Fixed newline rendering (`\n` instead of `\\n`)
- Fixed duplicate title in display
- Fixed players with 0 stats not appearing in rankings
- Fixed DEX calculation to include shiny count
- Fixed stat handler method for Minecraft 1.21.1 compatibility

### 🔧 Technical Improvements
- **ServerTickEvents hook**: Uses Fabric's `END_SERVER_TICK` for automatic refresh cycles
- **Optimized stat save**: Calls `ServerStatHandler.save()` to persist stats without server lag
- **Thread-safe operations**: Uses `AtomicLong` for concurrent refresh tracking
- **No browser storage**: Client-side cache management (no localStorage/sessionStorage)

## 📊 DEX Calculation

The DEX leaderboard now shows the true Pokédex count:
```
DEX = Pokémon Captured + Pokémon Evolved + Shinies + 1 (Starter)
```

Example:
- Captured: 50
- Evolved: 12
- Shinies: 5
- **DEX Total: 68**

## ⚙️ Configuration

New options in `config/cobbletop/cobbletop.yml`:

```yaml
headerSeparator: "──────────────────────────────"
footerSeparator: "──────────────────────────────"
footerLabel: "Last line: You"
refreshSeconds: 30
```

## 🎮 Usage

Placeholders remain the same:
- `%cobbletop:shinies_all%` - Top 10 shinies + your rank
- `%cobbletop:dex_all%` - Top 10 dex + your rank

## 📦 Requirements

- Fabric API 0.40.0+
- Minecraft 1.21.1+
- LuckPerms (for prefix support)
- eu.pb4.placeholder-api

## 🔄 Migration

No breaking changes. Existing configurations will work automatically.

## 🐞 Known Issues

None at this time.

## 📝 Technical Details

- **Auto-refresh interval**: 30 seconds (configurable)
- **Force refresh debounce**: 500ms minimum between refreshes
- **Performance**: Negligible impact with 15+ players

---

**Release Date**: February 1, 2026