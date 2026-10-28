package ru.sloboda.colony;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Набор блоков одной породы дерева. Определяется по id бревна, поэтому
 * автоматически работает с новыми породами (например, тополем из 26.3) и модовыми.
 */
public record WoodSet(Block log, Block planks, Block slab, Block fence, Block door) {
    public static final String DEFAULT_LOG = "minecraft:oak_log";

    public static WoodSet fromLogId(String logId) {
        Identifier id = Identifier.tryParse(logId);
        if (id == null) {
            id = Identifier.tryParse(DEFAULT_LOG);
        }
        String ns = id.getNamespace();
        String path = id.getPath().replace("stripped_", "");
        String base = stripSuffix(path, "_log", "_stem", "_wood", "_hyphae");

        Block log = find(ns, path, Blocks.OAK_LOG);
        if (log == Blocks.OAK_LOG) {
            log = find(ns, base + "_log", find(ns, base + "_stem", Blocks.OAK_LOG));
        }
        return new WoodSet(
                log,
                find(ns, base + "_planks", Blocks.OAK_PLANKS),
                find(ns, base + "_slab", Blocks.OAK_SLAB),
                find(ns, base + "_fence", Blocks.OAK_FENCE),
                find(ns, base + "_door", Blocks.OAK_DOOR));
    }

    private static String stripSuffix(String path, String... suffixes) {
        for (String s : suffixes) {
            if (path.endsWith(s)) {
                return path.substring(0, path.length() - s.length());
            }
        }
        return path;
    }

    private static Block find(String ns, String path, Block fallback) {
        Identifier id = Identifier.tryParse(ns + ":" + path);
        if (id == null) {
            return fallback;
        }
        return BuiltInRegistries.BLOCK.getOptional(id).orElse(fallback);
    }
}
