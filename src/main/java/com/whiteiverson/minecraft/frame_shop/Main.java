package com.whiteiverson.minecraft.frame_shop;

import com.whiteiverson.minecraft.frame_shop.Commands.FrameShopReloadCommand;
import com.whiteiverson.minecraft.frame_shop.Services.EssentialsWorthService;
import com.whiteiverson.minecraft.frame_shop.Services.VaultService;
import com.whiteiverson.minecraft.frame_shop.Shops.ShopListener;
import com.whiteiverson.minecraft.frame_shop.Utilities.MessageUtil;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {
    private static Main instance;

    private VaultService vaultService;
    private EssentialsWorthService worthService;
    private MessageUtil messageUtil;

    public static Main getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        messageUtil = new MessageUtil(this);
        vaultService = new VaultService(this);
        worthService = new EssentialsWorthService(this);

        if (!vaultService.setupEconomy()) {
            getLogger().severe("Vault economy provider not found. Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (!worthService.isAvailable()) {
            getLogger().severe("Essentials plugin not found. Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getPluginManager().registerEvents(new ShopListener(this), this);

        PluginCommand reloadCommand = getCommand("fsreload");
        if (reloadCommand != null) {
            reloadCommand.setExecutor(new FrameShopReloadCommand(this));
        }

        getLogger().info(messageUtil.color(messageUtil.get("messages.plugin-enabled")));
    }

    @Override
    public void onDisable() {
        getLogger().info(messageUtil.color(messageUtil.get("messages.plugin-disabled")));
    }

    public void reloadPlugin() {
        reloadConfig();
    }

    public VaultService getVaultService() {
        return vaultService;
    }

    public EssentialsWorthService getWorthService() {
        return worthService;
    }

    public MessageUtil getMessageUtil() {
        return messageUtil;
    }
}
