package com.whiteiverson.minecraft.frame_shop.Utilities;

import com.whiteiverson.minecraft.frame_shop.Main;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.Map;

public class MessageUtil {
    private final Main plugin;
    private final DecimalFormat moneyFormat = new DecimalFormat("0.00");

    public MessageUtil(Main plugin) {
        this.plugin = plugin;
        this.moneyFormat.setRoundingMode(RoundingMode.HALF_UP);
    }

    public String get(String path) {
        FileConfiguration config = plugin.getConfig();
        return config.getString(path, "");
    }

    public String prefixed(String path) {
        return color(get(path));
    }

    public String format(String path, Map<String, String> placeholders) {
        String message = get(path);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return color(message);
    }

    public String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public String money(double value) {
        return moneyFormat.format(value);
    }
}
