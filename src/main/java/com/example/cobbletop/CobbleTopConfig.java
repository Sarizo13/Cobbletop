package com.example.cobbletop;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class CobbleTopConfig {

    public int refreshSeconds = 60;
    public int nameColWidth = 18;

    public String titleShinies = "<dark_gray>────</dark_gray> <light_purple><bold>✨ TOP 10 SHINIES</bold></light_purple> <dark_gray>────</dark_gray>";
    public String titleDex = "<dark_gray>────</dark_gray> <gold><bold>🏆 TOP 10 DEX</bold></gold> <dark_gray>────</dark_gray>";

    public String lineFormatShinies = "{badge}{rank}<dark_gray>┃</dark_gray> {namePadded}<dark_gray>•</dark_gray> <light_purple>{value}</>";
    public String lineFormatDex     = "{badge}{rank}<dark_gray>┃</dark_gray> {namePadded}<dark_gray>•</dark_gray> <aqua>{value}</>";

    public String emptyLineFormat = "<dark_gray>{rank}</dark_gray> <dark_gray>┃</dark_gray> <gray>-</gray>";

    public String meLineFormatShinies = "{lead}<dark_gray>┃</dark_gray> <yellow>{pos}</yellow> <dark_gray>┃</dark_gray> {namePadded}<dark_gray>•</dark_gray> <light_purple>{value}</>";
    public String meLineFormatDex     = "{lead}<dark_gray>┃</dark_gray> <yellow>{pos}</yellow> <dark_gray>┃</dark_gray> {namePadded}<dark_gray>•</dark_gray> <aqua>{value}</>";

    public static CobbleTopConfig loadOrCreate(Path file) throws IOException {
        if (!Files.exists(file)) {
            Files.createDirectories(file.getParent());
            CobbleTopConfig cfg = new CobbleTopConfig();
            save(file, cfg);
            return cfg;
        }

        Yaml yaml = new Yaml(); // IMPORTANT: pas de Constructor(Class) -> compatible
        try (InputStream in = Files.newInputStream(file)) {
            Object obj = yaml.load(in);
            CobbleTopConfig cfg = fromObject(obj);
            return (cfg == null) ? new CobbleTopConfig() : cfg;
        }
    }

    public static void save(Path file, CobbleTopConfig cfg) throws IOException {
        DumperOptions opt = new DumperOptions();
        opt.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        opt.setPrettyFlow(true);
        opt.setIndent(2);

        Yaml yaml = new Yaml(opt);

        Files.createDirectories(file.getParent());
        try (OutputStreamWriter w = new OutputStreamWriter(Files.newOutputStream(file), StandardCharsets.UTF_8)) {
            yaml.dump(toMap(cfg), w);
        }
    }

    @SuppressWarnings("unchecked")
    private static CobbleTopConfig fromObject(Object obj) {
        if (!(obj instanceof Map)) return null;
        Map<String, Object> m = (Map<String, Object>) obj;

        CobbleTopConfig cfg = new CobbleTopConfig();

        cfg.refreshSeconds = getInt(m, "refreshSeconds", cfg.refreshSeconds);
        cfg.nameColWidth   = getInt(m, "nameColWidth", cfg.nameColWidth);

        cfg.titleShinies = getStr(m, "titleShinies", cfg.titleShinies);
        cfg.titleDex     = getStr(m, "titleDex", cfg.titleDex);

        cfg.lineFormatShinies = getStr(m, "lineFormatShinies", cfg.lineFormatShinies);
        cfg.lineFormatDex     = getStr(m, "lineFormatDex", cfg.lineFormatDex);

        cfg.emptyLineFormat = getStr(m, "emptyLineFormat", cfg.emptyLineFormat);

        cfg.meLineFormatShinies = getStr(m, "meLineFormatShinies", cfg.meLineFormatShinies);
        cfg.meLineFormatDex     = getStr(m, "meLineFormatDex", cfg.meLineFormatDex);
        cfg.footerSeparator = getStr(m, "footerSeparator", cfg.footerSeparator);
        cfg.footerLabel     = getStr(m, "footerLabel", cfg.footerLabel);


        return cfg;
    }

    private static Map<String, Object> toMap(CobbleTopConfig cfg) {
        // LinkedHashMap pour garder l'ordre joli dans le fichier
        java.util.LinkedHashMap<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("refreshSeconds", cfg.refreshSeconds);
        m.put("nameColWidth", cfg.nameColWidth);

        m.put("titleShinies", cfg.titleShinies);
        m.put("titleDex", cfg.titleDex);

        m.put("lineFormatShinies", cfg.lineFormatShinies);
        m.put("lineFormatDex", cfg.lineFormatDex);

        m.put("emptyLineFormat", cfg.emptyLineFormat);

        m.put("meLineFormatShinies", cfg.meLineFormatShinies);
        m.put("meLineFormatDex", cfg.meLineFormatDex);
        m.put("footerSeparator", cfg.footerSeparator);
        m.put("footerLabel", cfg.footerLabel);


        return m;
    }

    private static int getInt(Map<String, Object> m, String k, int def) {
        Object v = m.get(k);
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof String) {
            try { return Integer.parseInt((String) v); } catch (Exception ignored) {}
        }
        return def;
    }

    private static String getStr(Map<String, Object> m, String k, String def) {
        Object v = m.get(k);
        return (v == null) ? def : String.valueOf(v);
    }

    public String footerSeparator = "<dark_gray>────────────────────────────</dark_gray>";
    public String footerLabel = "<gray>Dernière ligne :</gray> <yellow>toi</yellow>";

}
