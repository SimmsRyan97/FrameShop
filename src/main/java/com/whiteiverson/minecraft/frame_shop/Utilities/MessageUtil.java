package com.whiteiverson.minecraft.frame_shop.Utilities;

import com.whiteiverson.minecraft.frame_shop.Main;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageUtil {
    private static final Pattern HEX_WITH_AMPERSAND = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern HEX_PLAIN = Pattern.compile("(?<!&)#([A-Fa-f0-9]{6})");

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
        if (message == null || message.isEmpty()) {
            return "";
        }

        String withHex = applyHexColors(message, HEX_WITH_AMPERSAND);
        withHex = applyHexColors(withHex, HEX_PLAIN);
        return ChatColor.translateAlternateColorCodes('&', withHex);
    }

    private String applyHexColors(String message, Pattern pattern) {
        Matcher matcher = pattern.matcher(message);
        StringBuffer output = new StringBuffer();

        while (matcher.find()) {
            String hex = matcher.group(1);
            String replacement = ChatColor.of("#" + hex).toString();
            matcher.appendReplacement(output, Matcher.quoteReplacement(replacement));
        }

        matcher.appendTail(output);
        return output.toString();
    }

    public String money(double value) {
        return moneyFormat.format(value);
    }
}
