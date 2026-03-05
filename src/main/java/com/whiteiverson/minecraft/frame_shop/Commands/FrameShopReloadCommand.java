package com.whiteiverson.minecraft.frame_shop.Commands;

import com.whiteiverson.minecraft.frame_shop.Main;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class FrameShopReloadCommand implements CommandExecutor {
    private final Main plugin;

    public FrameShopReloadCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("frameshop.reload")) {
            sender.sendMessage(plugin.getMessageUtil().prefixed("messages.no-permission-create"));
            return true;
        }

        plugin.reloadPlugin();
        sender.sendMessage(plugin.getMessageUtil().prefixed("messages.plugin-reloaded"));
        return true;
    }
}
