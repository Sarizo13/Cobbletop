package com.example.cobbletop;

import com.google.gson.*;
import eu.pb4.placeholders.api.PlaceholderContext;
import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.model.user.User;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class CobbleTopMod implements ModInitializer {

    private static final String WORLD_FOLDER = "world";
    private static final String KEY_SHINIES = "cobblemon:shinies_captured";
    private static final String KEY_DEX = "cobblemon:dex_entries";

    private static final long MIN_FORCE_REFRESH_INTERVAL_MS = 500;

    private final AtomicReference<Cache> cacheRef = new AtomicReference<>(Cache.empty());
    private final Map<UUID, String> prefixCache = new ConcurrentHashMap<>();

    private volatile long forceRefreshAtMs = 0;
    private final AtomicLong lastForceRefreshMs = new AtomicLong(0);

    // IMPORTANT: jamais null
    private volatile CobbleTopConfig config = new CobbleTopConfig();

    private volatile Duration refreshEvery = Duration.ofSeconds(60);
    private Path configFile;

    @Override
    public void onInitialize() {
        // /config/cobbletop/cobbletop.yml
        this.configFile = FabricLoader.getInstance()
                .getConfigDir()
                .resolve("cobbletop")
                .resolve("cobbletop.yml"); // getConfigDir() -> dossier config [web:507][web:501]

        // Charge la config (ou crée le fichier), mais ne doit jamais rendre config null
        boolean ok = reloadConfigInternal(false);
        if (!ok) {
            // fallback: on garde les defaults (config déjà initialisée)
            System.err.println("[CobbleTop] Failed to load config, using defaults. Check console for details.");
        }

        registerPlaceholders();
        registerCommands();
        hookLuckPermsEvents();
    }

    private void registerPlaceholders() {
        // all-in-one (multilines \n)
        Placeholders.register(id("cobbletop", "shinies_all"),
                (ctx, arg) -> PlaceholderResult.value(allBoard(ctx, Board.SHINIES)));

        Placeholders.register(id("cobbletop", "dex_all"),
                (ctx, arg) -> PlaceholderResult.value(allBoard(ctx, Board.DEX)));
    }

    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("cobbletop")
                    .requires(src -> src.hasPermissionLevel(2))
                    .then(CommandManager.literal("reload")
                            .executes(ctx -> {
                                boolean ok = reloadConfigInternal(true);
                                if (ok) {
                                    ctx.getSource().sendFeedback(() -> Text.literal("[CobbleTop] Config reloaded."), false);
                                } else {
                                    ctx.getSource().sendError(Text.literal("[CobbleTop] Reload failed (check console). Keeping previous config."));
                                }
                                return 1;
                            })
                    )
            );
        }); // v2 signature dispatcher, registryAccess, environment [web:531]
    }

    // Reload safe: ne met JAMAIS this.config à null, et n'applique que si OK
    private boolean reloadConfigInternal(boolean forceRefresh) {
        try {
            CobbleTopConfig newCfg = CobbleTopConfig.loadOrCreate(configFile);

            if (newCfg == null) {
                System.err.println("[CobbleTop] Config load returned null, keeping previous config.");
                return false;
            }

            this.config = newCfg;

            int sec = Math.max(5, newCfg.refreshSeconds);
            this.refreshEvery = Duration.ofSeconds(sec);

            if (forceRefresh) requestForceRefresh();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private Identifier id(String ns, String path) {
        return Identifier.of(ns, path);
    }

    private String phTitle(PlaceholderContext ctx, Board board) {
        refreshIfNeeded(ctx);
        CobbleTopConfig cfg = this.config; // non-null
        return (board == Board.SHINIES) ? cfg.titleShinies : cfg.titleDex;
    }

    private String lineFor(PlaceholderContext ctx, Board board, int rank1) {
        refreshIfNeeded(ctx);
        Cache cache = cacheRef.get();
        List<Entry> list = (board == Board.SHINIES) ? cache.topShinies : cache.topDex;

        String rankText = "#" + pad2(rank1);

        CobbleTopConfig cfg = this.config; // non-null

        if (rank1 - 1 < 0 || rank1 - 1 >= list.size()) {
            return applyFormat(cfg.emptyLineFormat, Map.of(
                    "rank", rankText
            ));
        }

        Entry e = list.get(rank1 - 1);

        String rankColor;
        String badge;
        if (rank1 == 1) { rankColor = "<gold>"; badge = "<gold>★</gold> "; }
        else if (rank1 == 2) { rankColor = "<gray>"; badge = "<gray>✦</gray> "; }
        else if (rank1 == 3) { rankColor = "<color:#cd7f32>"; badge = "<color:#cd7f32>✧</color> "; }
        else { rankColor = "<dark_gray>"; badge = "  "; }

        String rankPart = rankColor + rankText + "</> ";

        int width = Math.max(6, cfg.nameColWidth);
        String paddedPlain = padRightPlain(e.plainName, width);
        String spacesToAdd = paddedPlain.substring(Math.min(e.plainName.length(), paddedPlain.length()));
        String namePadded = e.name + spacesToAdd;

        String fmt = (board == Board.SHINIES) ? cfg.lineFormatShinies : cfg.lineFormatDex;
        return applyFormat(fmt, Map.of(
                "badge", badge,
                "rank", rankPart,
                "name", e.name,
                "namePadded", namePadded,
                "value", String.valueOf(e.value)
        ));
    }

    private String meLine(PlaceholderContext ctx, Board board) {
        refreshIfNeeded(ctx);

        CobbleTopConfig cfg = this.config; // non-null

        if (ctx.player() == null) {
            return "<dark_gray>Toi</dark_gray> <dark_gray>┃</dark_gray> <gray>-</gray>";
        }

        UUID me = ctx.player().getUuid();
        Cache cache = cacheRef.get();
        List<Entry> full = (board == Board.SHINIES) ? cache.allShinies : cache.allDex;

        int pos = -1;
        Entry mine = null;
        for (int i = 0; i < full.size(); i++) {
            if (full.get(i).uuid.equals(me)) {
                pos = i + 1;
                mine = full.get(i);
                break;
            }
        }

        if (pos == -1 || mine == null) {
            return "<dark_gray>Toi</dark_gray> <dark_gray>┃</dark_gray> <gray>-</gray>";
        }

        String lead = (pos <= 10) ? "<gold>★</gold> " : "<dark_gray>…</dark_gray> ";
        String posText = "#" + pad2Clamp(pos);

        int width = Math.max(6, cfg.nameColWidth);
        String paddedPlain = padRightPlain(mine.plainName, width);
        String spacesToAdd = paddedPlain.substring(Math.min(mine.plainName.length(), paddedPlain.length()));
        String namePadded = mine.name + spacesToAdd;

        String fmt = (board == Board.SHINIES) ? cfg.meLineFormatShinies : cfg.meLineFormatDex;
        return applyFormat(fmt, Map.of(
                "lead", lead,
                "pos", posText,
                "name", mine.name,
                "namePadded", namePadded,
                "value", String.valueOf(mine.value)
        ));
    }

    private void refreshIfNeeded(PlaceholderContext ctx) {
        Cache cache = cacheRef.get();
        long now = System.currentTimeMillis();

        boolean forced = forceRefreshAtMs != 0;
        if (!forced && now - cache.lastRefreshMs < refreshEvery.toMillis()) return;

        MinecraftServer server = ctx.server();
        if (server == null) return;

        cache = cacheRef.get();
        now = System.currentTimeMillis();
        forced = forceRefreshAtMs != 0;
        if (!forced && now - cache.lastRefreshMs < refreshEvery.toMillis()) return;

        try {
            Cache refreshed = buildCache(server.getRunDirectory());
            cacheRef.set(refreshed);
            forceRefreshAtMs = 0;
        } catch (Exception e) {
            e.printStackTrace();
            Cache fallback = cacheRef.get();
            cacheRef.set(new Cache(fallback.topShinies, fallback.topDex, fallback.allShinies, fallback.allDex, now));
            forceRefreshAtMs = 0;
        }
    }

    private Cache buildCache(Path runDir) throws IOException {
        Map<UUID, String> names = loadUserCache(runDir.resolve("usercache.json"));

        Path statsDir = runDir.resolve(WORLD_FOLDER).resolve("stats");
        if (!Files.isDirectory(statsDir)) {
            return Cache.emptyWithNow();
        }

        List<Entry> shinies = new ArrayList<>();
        List<Entry> dex = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(statsDir, "*.json")) {
            for (Path file : stream) {
                UUID uuid = uuidFromStatsFilename(file.getFileName().toString());
                if (uuid == null) continue;

                JsonObject root = parseJsonObject(file);
                JsonObject stats = root.has("stats") ? root.getAsJsonObject("stats") : null;
                if (stats == null) continue;

                JsonObject custom = stats.has("minecraft:custom") ? stats.getAsJsonObject("minecraft:custom") : null;
                if (custom == null) continue;

                int vShinies = getInt(custom, KEY_SHINIES);
                int vDex = getInt(custom, KEY_DEX);

                String baseName = names.getOrDefault(uuid, shortUuid(uuid));

                requestLuckPermsPrefixAsync(uuid);

                String prefix = prefixCache.getOrDefault(uuid, "");
                String displayName = prefix + "<white>" + baseName + "</white>";

                if (vShinies > 0) shinies.add(new Entry(uuid, displayName, baseName, vShinies));
                if (vDex > 0) dex.add(new Entry(uuid, displayName, baseName, vDex));
            }
        }

        shinies.sort(Comparator.comparingInt((Entry e) -> e.value).reversed().thenComparing(e -> e.plainName));
        dex.sort(Comparator.comparingInt((Entry e) -> e.value).reversed().thenComparing(e -> e.plainName));

        List<Entry> topShinies = shinies.subList(0, Math.min(10, shinies.size()));
        List<Entry> topDex = dex.subList(0, Math.min(10, dex.size()));

        return new Cache(
                new ArrayList<>(topShinies),
                new ArrayList<>(topDex),
                new ArrayList<>(shinies),
                new ArrayList<>(dex),
                System.currentTimeMillis()
        );
    }

    private void requestLuckPermsPrefixAsync(UUID uuid) {
        try {
            LuckPerms lp = LuckPermsProvider.get();

            User loaded = lp.getUserManager().getUser(uuid);
            if (loaded != null) {
                updatePrefixCache(uuid, safePrefix(loaded));
                return;
            }

            lp.getUserManager().loadUser(uuid).thenAccept(user -> {
                if (user == null) return;
                updatePrefixCache(uuid, safePrefix(user));
            });
        } catch (Throwable ignored) {
        }
    }

    private void updatePrefixCache(UUID uuid, String rawPrefix) {
        String converted = legacyToPb4(rawPrefix);
        String old = prefixCache.put(uuid, converted);
        if (!Objects.equals(old, converted)) {
            requestForceRefresh();
        }
    }

    private void requestForceRefresh() {
        long now = System.currentTimeMillis();
        long last = lastForceRefreshMs.get();
        if (now - last < MIN_FORCE_REFRESH_INTERVAL_MS) return;
        if (lastForceRefreshMs.compareAndSet(last, now)) {
            forceRefreshAtMs = now;
        }
    }

    private String safePrefix(User user) {
        try {
            CachedMetaData meta = user.getCachedData().getMetaData();
            String prefix = meta.getPrefix();
            return prefix == null ? "" : prefix;
        } catch (Throwable t) {
            return "";
        }
    }

    private String legacyToPb4(String s) {
        if (s == null) return "";
        s = s.replace('§', '&');

        s = s.replaceAll("(?i)&r", "<reset>");
        s = s.replaceAll("(?i)&l", "<bold>");
        s = s.replaceAll("(?i)&o", "<italic>");
        s = s.replaceAll("(?i)&n", "<underlined>");
        s = s.replaceAll("(?i)&m", "<strikethrough>");
        s = s.replaceAll("(?i)&k", "");

        s = s.replaceAll("(?i)&0", "<black>");
        s = s.replaceAll("(?i)&1", "<dark_blue>");
        s = s.replaceAll("(?i)&2", "<dark_green>");
        s = s.replaceAll("(?i)&3", "<dark_aqua>");
        s = s.replaceAll("(?i)&4", "<dark_red>");
        s = s.replaceAll("(?i)&5", "<dark_purple>");
        s = s.replaceAll("(?i)&6", "<gold>");
        s = s.replaceAll("(?i)&7", "<gray>");
        s = s.replaceAll("(?i)&8", "<dark_gray>");
        s = s.replaceAll("(?i)&9", "<blue>");
        s = s.replaceAll("(?i)&a", "<green>");
        s = s.replaceAll("(?i)&b", "<aqua>");
        s = s.replaceAll("(?i)&c", "<red>");
        s = s.replaceAll("(?i)&d", "<light_purple>");
        s = s.replaceAll("(?i)&e", "<yellow>");
        s = s.replaceAll("(?i)&f", "<white>");

        s = s.replaceAll("(?i)&[0-9A-FK-OR]", "");
        return s;
    }

    private void hookLuckPermsEvents() {
        try {
            LuckPerms lp = LuckPermsProvider.get();
            lp.getEventBus().subscribe(this, UserDataRecalculateEvent.class, e -> {
                UUID uuid = e.getUser().getUniqueId();
                requestLuckPermsPrefixAsync(uuid);
                requestForceRefresh();
            });
        } catch (Throwable ignored) {
        }
    }

    private String applyFormat(String fmt, Map<String, String> vars) {
        String out = fmt;
        for (Map.Entry<String, String> en : vars.entrySet()) {
            out = out.replace("{" + en.getKey() + "}", en.getValue());
        }
        return out;
    }

    private Map<UUID, String> loadUserCache(Path usercacheJson) throws IOException {
        if (!Files.isRegularFile(usercacheJson)) return Map.of();

        String txt = Files.readString(usercacheJson, StandardCharsets.UTF_8);
        JsonArray arr = JsonParser.parseString(txt).getAsJsonArray();

        Map<UUID, String> map = new HashMap<>();
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();

            if (!o.has("uuid") || !o.has("name")) continue;
            String uuidStr = o.get("uuid").getAsString();
            String name = o.get("name").getAsString();

            try {
                UUID uuid = UUID.fromString(uuidStr);
                map.put(uuid, name);
            } catch (IllegalArgumentException ignored) {}
        }
        return map;
    }

    private UUID uuidFromStatsFilename(String name) {
        if (!name.endsWith(".json")) return null;
        String base = name.substring(0, name.length() - 5);
        try {
            return UUID.fromString(base);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private JsonObject parseJsonObject(Path file) throws IOException {
        String txt = Files.readString(file, StandardCharsets.UTF_8);
        return JsonParser.parseString(txt).getAsJsonObject();
    }

    private int getInt(JsonObject obj, String key) {
        if (!obj.has(key)) return 0;
        try {
            return obj.get(key).getAsInt();
        } catch (Exception e) {
            return 0;
        }
    }

    private String shortUuid(UUID uuid) {
        return uuid.toString().substring(0, 8);
    }

    private String pad2(int n) {
        return (n < 10) ? "0" + n : String.valueOf(n);
    }

    private String pad2Clamp(int n) {
        if (n < 100) return pad2(n);
        return String.valueOf(n);
    }

    private String padRightPlain(String plain, int width) {
        if (plain == null) plain = "";
        if (plain.length() >= width) return plain;
        return plain + " ".repeat(width - plain.length());
    }

    private enum Board { SHINIES, DEX }

    private static class Entry {
        final UUID uuid;
        final String name;
        final String plainName;
        final int value;

        Entry(UUID uuid, String name, String plainName, int value) {
            this.uuid = uuid;
            this.name = name;
            this.plainName = plainName;
            this.value = value;
        }
    }

    private static class Cache {
        final List<Entry> topShinies;
        final List<Entry> topDex;
        final List<Entry> allShinies;
        final List<Entry> allDex;
        final long lastRefreshMs;

        Cache(List<Entry> topShinies, List<Entry> topDex, List<Entry> allShinies, List<Entry> allDex, long lastRefreshMs) {
            this.topShinies = topShinies;
            this.topDex = topDex;
            this.allShinies = allShinies;
            this.allDex = allDex;
            this.lastRefreshMs = lastRefreshMs;
        }

        static Cache empty() {
            return new Cache(List.of(), List.of(), List.of(), List.of(), 0);
        }

        static Cache emptyWithNow() {
            return new Cache(List.of(), List.of(), List.of(), List.of(), System.currentTimeMillis());
        }
    }

    private String allBoard(PlaceholderContext ctx, Board board) {
        refreshIfNeeded(ctx);

        CobbleTopConfig cfg = this.config;

        StringBuilder sb = new StringBuilder();

        // Titre
        sb.append(phTitle(ctx, board));

        // Top 10
        for (int i = 1; i <= 10; i++) {
            sb.append("\n").append(lineFor(ctx, board, i));
        }

        // Séparation + info
        if (cfg.footerSeparator != null && !cfg.footerSeparator.isEmpty()) {
            sb.append("\n").append(cfg.footerSeparator);
        }
        if (cfg.footerLabel != null && !cfg.footerLabel.isEmpty()) {
            sb.append("\n").append(cfg.footerLabel);
        }

        // Ligne "me"
        sb.append("\n").append(meLine(ctx, board));

        return sb.toString();
    }

}
