package io.github.ankushui18.fbaoffline;

import java.util.Locale;

/** FBNeo driver prefixes, libretro folder selectors and accepted loose-file suffixes. */
enum ConsoleSystem {
    A2600("a26", "a2600", "Atari 2600", "a26"),
    ASTRO("astro", "astro", "Bally Astrocade", "astro"),
    CHANNEL_F("chf", "chf", "Fairchild Channel F", "chf"),
    COLECO("cv", "coleco", "ColecoVision", "col", "cv"),
    FDS("fds", "fds", "Famicom Disk System", "fds"),
    GBA("gba", "gba", "Game Boy Advance", "gba", "srl"),
    GAME_GEAR("gg", "gamegear", "Game Gear", "gg"),
    MEGA_DRIVE("md", "megadrive", "Genesis / Mega Drive", "md", "gen"),
    MSX("msx", "msx", "MSX1", "cas", "mx1"),
    NES("nes", "nes", "NES / Famicom", "nes", "unf", "unif"),
    NGP("ngp", "ngp", "Neo Geo Pocket / Color", "ngp", "ngc", "npg"),
    PCE("pce", "pce", "PC Engine", "pce"),
    SEGA_32X("s32x", "32x", "Sega 32X", "32x"),
    SG1000("sg1k", "sg1000", "SG-1000", "sg", "sg1000"),
    SGX("sgx", "sgx", "SuperGrafx", "pce", "sgx"),
    SMS("sms", "sms", "Master System", "sms"),
    SNES("snes", "snes", "SNES / Super Famicom", "sfc", "smc"),
    SPECTRUM("spec", "spectrum", "ZX Spectrum", "tap", "tzx", "z80"),
    TG16("tg", "tg16", "TurboGrafx-16", "pce", "tg16");

    private static final ConsoleSystem[] SYSTEMS = values();
    final String prefix, folder, label;
    private final String[] extensions;

    ConsoleSystem(String prefix, String folder, String label, String... extensions) {
        this.prefix = prefix;
        this.folder = folder;
        this.label = label;
        this.extensions = extensions;
    }

    static ConsoleSystem fromPrefix(String prefix) {
        for (ConsoleSystem system : SYSTEMS) if (system.prefix.equals(prefix)) return system;
        return null;
    }

    static ConsoleSystem fromArchive(String name) {
        if (name == null) return null;
        int separator = name.indexOf('_');
        return separator > 0 ? fromPrefix(name.substring(0, separator)) : null;
    }

    static String extension(String name) {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    boolean accepts(String extension) {
        if (extension.equals("bin") || extension.equals("rom")) return true;
        for (String suffix : extensions) if (suffix.equals(extension)) return true;
        return false;
    }

    static boolean isDisc(String name) {
        String ext = extension(name);
        if (ext.equals("chd") || ext.equals("cue") || ext.equals("ccd") || ext.equals("iso") || ext.equals("m3u")) return true;
        if (!ext.equals("zip")) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.startsWith("neocd_") || lower.startsWith("pcecd_") || lower.startsWith("segacd_");
    }

    static boolean isRaw(String name) {
        String extension = extension(name);
        for (ConsoleSystem system : SYSTEMS) if (system.accepts(extension)) return true;
        return false;
    }
}
