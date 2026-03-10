package com.whiteiverson.minecraft.frame_shop.Services;

import com.whiteiverson.minecraft.frame_shop.Main;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class EssentialsWorthService {
    private final Main plugin;
    private Plugin essentialsPlugin;
    private File essentialsWorthFile;
    private FileConfiguration essentialsWorthConfig;
    private long worthFileLastModified = -1L;

    public EssentialsWorthService(Main plugin) {
        this.plugin = plugin;
        this.essentialsPlugin = plugin.getServer().getPluginManager().getPlugin("Essentials");
        refreshWorthFile();
    }

    public boolean isAvailable() {
        return essentialsPlugin != null && essentialsPlugin.isEnabled();
    }

    public Double getWorth(ItemStack itemStack) {
        if (!isAvailable() || itemStack == null || itemStack.getType().isAir()) {
            return null;
        }

        refreshWorthFile();

        Double enchantBonus = attemptEnchantmentBonusFromWorthFile(itemStack);

        Double direct = attemptDirectWorth(itemStack);
        if (direct != null) {
            return direct + (enchantBonus == null ? 0.0D : enchantBonus);
        }

        Double viaWorthManager = attemptWorthManager(itemStack);
        if (viaWorthManager != null) {
            return viaWorthManager + (enchantBonus == null ? 0.0D : enchantBonus);
        }

        Double fromWorthFile = attemptWorthFileLookup(itemStack);
        if (fromWorthFile != null) {
            return fromWorthFile + (enchantBonus == null ? 0.0D : enchantBonus);
        }

        if (enchantBonus != null && enchantBonus > 0.0D) {
            return enchantBonus;
        }

        return null;
    }

    private void refreshWorthFile() {
        if (essentialsPlugin == null) {
            essentialsPlugin = plugin.getServer().getPluginManager().getPlugin("Essentials");
        }

        if (essentialsPlugin == null) {
            essentialsWorthFile = null;
            essentialsWorthConfig = null;
            worthFileLastModified = -1L;
            return;
        }

        File worthFile = new File(essentialsPlugin.getDataFolder(), "worth.yml");
        if (!worthFile.exists()) {
            essentialsWorthFile = null;
            essentialsWorthConfig = null;
            worthFileLastModified = -1L;
            return;
        }

        long currentLastModified = worthFile.lastModified();

        if (essentialsWorthFile == null
                || !essentialsWorthFile.equals(worthFile)
                || essentialsWorthConfig == null
                || currentLastModified != worthFileLastModified) {
            essentialsWorthFile = worthFile;
            essentialsWorthConfig = YamlConfiguration.loadConfiguration(worthFile);
            worthFileLastModified = currentLastModified;
        }
    }

    private Double attemptWorthFileLookup(ItemStack itemStack) {
        if (essentialsWorthConfig == null) {
            return null;
        }

        for (String key : getCandidateWorthKeys(itemStack)) {
            Object raw = essentialsWorthConfig.get("worth." + key);
            Double parsed = parseNumeric(raw);
            if (parsed != null && parsed > 0.0D) {
                return parsed;
            }
        }

        return null;
    }

    private Double attemptEnchantmentBonusFromWorthFile(ItemStack itemStack) {
        if (essentialsWorthConfig == null) {
            return null;
        }

        Map<Enchantment, Integer> enchantments = getItemEnchantments(itemStack);
        if (enchantments.isEmpty()) {
            return null;
        }

        double totalBonus = 0.0D;
        boolean matchedAny = false;

        List<String> materialKeys = getCandidateWorthKeys(itemStack);
        double baseEnchantedBookWorth = getBaseMaterialWorth(Material.ENCHANTED_BOOK);
        boolean isEnchantedBookItem = itemStack.getType() == Material.ENCHANTED_BOOK;

        for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
            List<String> enchantKeys = getEnchantmentKeyCandidates(entry.getKey());
            int level = entry.getValue();
            Double bonus = findBestEnchantmentBonus(materialKeys, enchantKeys, level);

            if (!isEnchantedBookItem) {
                Double bookBonus = findBestEnchantmentBonus(getMaterialWorthKeys(Material.ENCHANTED_BOOK), enchantKeys, level);
                if (bookBonus != null) {
                    double bookEquivalent = baseEnchantedBookWorth + bookBonus;
                    bonus = bonus == null ? bookEquivalent : Math.max(bonus, bookEquivalent);
                }
            }

            if (bonus != null) {
                totalBonus += bonus;
                matchedAny = true;
            }
        }

        return matchedAny ? totalBonus : null;
    }

    private Double findBestEnchantmentBonus(List<String> materialKeys, List<String> enchantKeys, int level) {
        for (String materialKey : materialKeys) {
            for (String enchantKey : enchantKeys) {
                Double exact = parseWorthValue("worth." + materialKey + "|" + enchantKey + "|" + level);
                if (exact != null && exact > 0.0D) {
                    return exact;
                }
            }
        }

        for (String materialKey : materialKeys) {
            for (String enchantKey : enchantKeys) {
                Double materialAnyLevel = parseWorthValue("worth." + materialKey + "|" + enchantKey);
                if (materialAnyLevel != null && materialAnyLevel > 0.0D) {
                    return materialAnyLevel;
                }
            }
        }

        for (String enchantKey : enchantKeys) {
            Double globalExact = parseWorthValue("worth.any|" + enchantKey + "|" + level);
            if (globalExact != null && globalExact > 0.0D) {
                return globalExact;
            }
        }

        for (String enchantKey : enchantKeys) {
            Double globalAnyLevel = parseWorthValue("worth.any|" + enchantKey);
            if (globalAnyLevel != null && globalAnyLevel > 0.0D) {
                return globalAnyLevel;
            }
        }

        return null;
    }

    private Double parseWorthValue(String path) {
        if (essentialsWorthConfig == null) {
            return null;
        }

        Object raw = essentialsWorthConfig.get(path);
        return parseNumeric(raw);
    }

    private Map<Enchantment, Integer> getItemEnchantments(ItemStack itemStack) {
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return Collections.emptyMap();
        }

        Map<Enchantment, Integer> source;
        if (meta instanceof EnchantmentStorageMeta) {
            source = ((EnchantmentStorageMeta) meta).getStoredEnchants();
        } else {
            source = meta.getEnchants();
        }

        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Map.Entry<Enchantment, Integer>> entries = new ArrayList<>(source.entrySet());
        entries.sort(Comparator.comparing(entry -> entry.getKey().getKey().toString()));

        Map<Enchantment, Integer> ordered = new HashMap<>();
        for (Map.Entry<Enchantment, Integer> entry : entries) {
            ordered.put(entry.getKey(), entry.getValue());
        }
        return ordered;
    }

    private List<String> getEnchantmentKeyCandidates(Enchantment enchantment) {
        Set<String> keys = new LinkedHashSet<>();
        String namespaced = enchantment.getKey().toString().toLowerCase(Locale.ENGLISH);
        keys.add(namespaced);
        keys.add(namespaced.replace("minecraft:", ""));
        return new ArrayList<>(keys);
    }

    private List<String> getCandidateWorthKeys(ItemStack itemStack) {
        return getMaterialWorthKeys(itemStack.getType());
    }

    private List<String> getMaterialWorthKeys(Material materialType) {
        List<String> keys = new ArrayList<>();
        String material = materialType.name().toLowerCase(Locale.ENGLISH);

        String compact = material.replace("_", "");
        if (!compact.isEmpty()) {
            keys.add(compact);
        }

        keys.add(material);

        String namespaced = materialType.getKey().toString().toLowerCase(Locale.ENGLISH);
        keys.add(namespaced);
        keys.add(namespaced.replace("minecraft:", ""));

        return keys;
    }

    private double getBaseMaterialWorth(Material materialType) {
        if (essentialsWorthConfig == null) {
            return 0.0D;
        }

        for (String key : getMaterialWorthKeys(materialType)) {
            Double value = parseWorthValue("worth." + key);
            if (value != null && value > 0.0D) {
                return value;
            }
        }

        return 0.0D;
    }

    private Double attemptDirectWorth(ItemStack itemStack) {
        try {
            Method method = essentialsPlugin.getClass().getMethod("getWorth", ItemStack.class);
            Object value = method.invoke(essentialsPlugin, itemStack.clone());
            return parseNumeric(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Double attemptWorthManager(ItemStack itemStack) {
        try {
            Method getWorthMethod = essentialsPlugin.getClass().getMethod("getWorth");
            Object worthManager = getWorthMethod.invoke(essentialsPlugin);
            if (worthManager == null) {
                return null;
            }

            for (Method method : worthManager.getClass().getMethods()) {
                if (!method.getName().equalsIgnoreCase("getPrice") && !method.getName().equalsIgnoreCase("getWorth")) {
                    continue;
                }

                Class<?>[] params = method.getParameterTypes();
                if (params.length == 1 && params[0].isAssignableFrom(ItemStack.class)) {
                    Object value = method.invoke(worthManager, itemStack.clone());
                    Double parsed = parseNumeric(value);
                    if (parsed != null) {
                        return parsed;
                    }
                }
            }
        } catch (Exception ignored) {
            return null;
        }

        return null;
    }

    private Double parseNumeric(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Double) {
            return (Double) value;
        }

        if (value instanceof Integer) {
            return ((Integer) value).doubleValue();
        }

        if (value instanceof Long) {
            return ((Long) value).doubleValue();
        }

        if (value instanceof BigDecimal) {
            return ((BigDecimal) value).doubleValue();
        }

        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }

        return null;
    }
}
