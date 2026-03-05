package com.whiteiverson.minecraft.frame_shop.Services;

import com.whiteiverson.minecraft.frame_shop.Main;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

        Double direct = attemptDirectWorth(itemStack);
        if (direct != null) {
            return direct;
        }

        Double viaWorthManager = attemptWorthManager(itemStack);
        if (viaWorthManager != null) {
            return viaWorthManager;
        }

        Double fromWorthFile = attemptWorthFileLookup(itemStack);
        if (fromWorthFile != null) {
            return fromWorthFile;
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

    private List<String> getCandidateWorthKeys(ItemStack itemStack) {
        List<String> keys = new ArrayList<>();
        String material = itemStack.getType().name().toLowerCase(Locale.ENGLISH);

        String compact = material.replace("_", "");
        if (!compact.isEmpty()) {
            keys.add(compact);
        }

        keys.add(material);

        String namespaced = itemStack.getType().getKey().toString().toLowerCase(Locale.ENGLISH);
        keys.add(namespaced);
        keys.add(namespaced.replace("minecraft:", ""));

        return keys;
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
