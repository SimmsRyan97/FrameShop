package com.whiteiverson.minecraft.frame_shop.Shops;

import com.whiteiverson.minecraft.frame_shop.Main;
import com.whiteiverson.minecraft.frame_shop.Utilities.InventoryUtil;
import com.whiteiverson.minecraft.frame_shop.Utilities.MessageUtil;
import com.whiteiverson.minecraft.frame_shop.Utilities.PriceUtil;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.DyeColor;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class ShopListener implements Listener {
    private static final int SLOT_MINUS_STACK = 10;
    private static final int SLOT_MINUS_ONE = 11;
    private static final int SLOT_PLUS_ONE = 15;
    private static final int SLOT_PLUS_STACK = 16;
    private static final int SLOT_ITEM_INFO = 13;
    private static final int SLOT_CUSTOM_AMOUNT = 21;
    private static final int SLOT_BUY = 23;

    private final Main plugin;
    private final MessageUtil messageUtil;
    private final Economy economy;

    private final Map<UUID, ShopSession> sessions = new HashMap<>();
    private final Set<UUID> awaitingChatAmount = new HashSet<>();
    private final Set<UUID> refreshingGui = new HashSet<>();

    public ShopListener(Main plugin) {
        this.plugin = plugin;
        this.messageUtil = plugin.getMessageUtil();
        this.economy = plugin.getVaultService().getEconomy();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSignCreate(SignChangeEvent event) {
        Player player = event.getPlayer();
        Block signBlock = event.getBlock();

        ItemFrame frame = getFrameAbove(signBlock);
        if (frame == null) {
            return;
        }

        if (!player.hasPermission("frameshop.create")) {
            event.setCancelled(true);
            return;
        }

        if (frame.getItem() == null || frame.getItem().getType().isAir()) {
            player.sendMessage(messageUtil.prefixed("messages.item-frame-empty"));
            return;
        }

        event.setLine(1, messageUtil.color(getBuyLabel()));
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!(signBlock.getState() instanceof Sign)) {
                return;
            }

            Sign placedSign = (Sign) signBlock.getState();
            if (!isShopSign(placedSign)) {
                return;
            }

            setSignActive(placedSign);
        });
        player.sendMessage(messageUtil.prefixed("messages.sign-created"));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSignRightClick(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (event.getClickedBlock() == null
                || (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR)) {
            return;
        }

        Block clicked = event.getClickedBlock();
        if (!(clicked.getState() instanceof Sign)) {
            return;
        }

        Sign sign = (Sign) clicked.getState();
        if (!isShopSign(sign)) {
            return;
        }

        event.setCancelled(true);

        Player player = event.getPlayer();
        if (!player.hasPermission("frameshop.use")) {
            player.sendMessage(messageUtil.prefixed("messages.no-permission-use"));
            return;
        }

        ItemFrame frame = getFrameAbove(clicked);
        if (frame == null || frame.getItem() == null || frame.getItem().getType().isAir()) {
            setSignInactive(sign);
            player.sendMessage(messageUtil.prefixed("messages.item-frame-empty"));
            return;
        }

        setSignActive(sign);

        ItemStack shopItem = frame.getItem().clone();
        shopItem.setAmount(1);

        Double baseWorth = plugin.getWorthService().getWorth(shopItem);
        if (baseWorth == null || baseWorth <= 0.0D) {
            player.sendMessage(messageUtil.prefixed("messages.no-worth-found"));
            return;
        }

        double taxPercent = plugin.getConfig().getDouble("settings.tax-percent", 0.0D);
        double unitPrice = PriceUtil.applyTax(baseWorth, taxPercent);

        ShopSession session = new ShopSession(clicked.getLocation(), shopItem, unitPrice, 1);
        sessions.put(player.getUniqueId(), session);

        openGui(player, session);
    }

    @EventHandler(ignoreCancelled = true)
    public void onGuiClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        String expectedTitle = messageUtil.color(plugin.getConfig().getString("settings.gui-title", "&8Buy Item"));
        if (!event.getView().getTitle().equals(expectedTitle)) {
            return;
        }

        event.setCancelled(true);

        ShopSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }

        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return;
        }

        int slot = event.getRawSlot();

        if (slot == SLOT_MINUS_ONE) {
            updateAmount(player, session, session.getAmount() - 1);
            return;
        }

        if (slot == SLOT_PLUS_ONE) {
            updateAmount(player, session, session.getAmount() + 1);
            return;
        }

        if (slot == SLOT_MINUS_STACK) {
            if (!hasStackControls(session)) {
                return;
            }
            updateAmount(player, session, session.getAmount() - session.getItemStack().getMaxStackSize());
            return;
        }

        if (slot == SLOT_PLUS_STACK) {
            if (!hasStackControls(session)) {
                return;
            }
            updateAmount(player, session, session.getAmount() + session.getItemStack().getMaxStackSize());
            return;
        }

        if (slot == SLOT_CUSTOM_AMOUNT) {
            awaitingChatAmount.add(player.getUniqueId());
            player.closeInventory();
            player.sendMessage(messageUtil.prefixed("messages.amount-prompt"));
            return;
        }

        if (slot == SLOT_BUY) {
            attemptPurchase(player, session);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onGuiDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        String expectedTitle = messageUtil.color(plugin.getConfig().getString("settings.gui-title", "&8Buy Item"));
        if (!event.getView().getTitle().equals(expectedTitle)) {
            return;
        }

        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ShopSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }

        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onGuiClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (refreshingGui.remove(uuid)) {
            return;
        }

        if (!awaitingChatAmount.contains(uuid)) {
            sessions.remove(uuid);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onChatAmountInput(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!awaitingChatAmount.contains(uuid)) {
            return;
        }

        event.setCancelled(true);
        String message = event.getMessage().trim();

        if (message.equalsIgnoreCase("cancel")) {
            awaitingChatAmount.remove(uuid);
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage(messageUtil.prefixed("messages.amount-cancelled"));
                ShopSession session = sessions.get(uuid);
                if (session != null) {
                    openGui(player, session);
                }
            });
            return;
        }

        int parsedAmount;
        try {
            parsedAmount = Integer.parseInt(message);
        } catch (NumberFormatException ex) {
            parsedAmount = -1;
        }

        int amount = parsedAmount;
        Bukkit.getScheduler().runTask(plugin, () -> {
            ShopSession session = sessions.get(uuid);
            if (session == null) {
                awaitingChatAmount.remove(uuid);
                return;
            }

            if (amount <= 0) {
                player.sendMessage(messageUtil.prefixed("messages.invalid-amount"));
                openGui(player, session);
                return;
            }

            session.setAmount(amount);
            awaitingChatAmount.remove(uuid);

            player.sendMessage(messageUtil.format("messages.amount-set", Collections.singletonMap("amount", String.valueOf(amount))));
            openGui(player, session);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onFrameBreak(HangingBreakEvent event) {
        if (!(event.getEntity() instanceof ItemFrame)) {
            return;
        }

        deactivateSignsBelow(event.getEntity().getLocation());
    }

    @EventHandler(ignoreCancelled = true)
    public void onFramePlace(HangingPlaceEvent event) {
        if (!(event.getEntity() instanceof ItemFrame)) {
            return;
        }

        ItemFrame frame = (ItemFrame) event.getEntity();
        Bukkit.getScheduler().runTask(plugin, () -> refreshSignsFromFrame(frame));
    }

    @EventHandler(ignoreCancelled = true)
    public void onFrameInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof ItemFrame)) {
            return;
        }

        ItemFrame frame = (ItemFrame) event.getRightClicked();
        Bukkit.getScheduler().runTask(plugin, () -> refreshSignsFromFrame(frame));
    }

    @EventHandler(ignoreCancelled = true)
    public void onFrameDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof ItemFrame)) {
            return;
        }

        ItemFrame frame = (ItemFrame) event.getEntity();
        Bukkit.getScheduler().runTask(plugin, () -> refreshSignsFromFrame(frame));
    }

    private void updateAmount(Player player, ShopSession session, int amount) {
        if (amount <= 0) {
            amount = 1;
        }

        session.setAmount(amount);
        openGui(player, session);
    }

    private void openGui(Player player, ShopSession session) {
        refreshingGui.add(player.getUniqueId());

        String title = messageUtil.color(plugin.getConfig().getString("settings.gui-title", "&8Buy Item"));
        Inventory gui = Bukkit.createInventory(null, 27, title);

        if (hasStackControls(session)) {
            gui.setItem(SLOT_MINUS_STACK, createButton(Material.RED_STAINED_GLASS_PANE, "&c-Stack", null));
            gui.setItem(SLOT_PLUS_STACK, createButton(Material.LIME_STAINED_GLASS_PANE, "&a+Stack", null));
        }
        gui.setItem(SLOT_MINUS_ONE, createButton(Material.ORANGE_STAINED_GLASS_PANE, "&c-1", null));
        gui.setItem(SLOT_PLUS_ONE, createButton(Material.GREEN_STAINED_GLASS_PANE, "&a+1", null));
        gui.setItem(SLOT_CUSTOM_AMOUNT, createButton(Material.PAPER, "&eType Amount", Collections.singletonList("&7Click to type amount in chat")));

        ItemStack info = session.getItemStack().clone();
        ItemMeta meta = info.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            lore.add(messageUtil.color("&7Price per item: &a$" + messageUtil.money(session.getUnitPrice())));
            lore.add(messageUtil.color("&7Amount: &e" + session.getAmount()));
            lore.add(messageUtil.color("&7Total: &a$" + messageUtil.money(session.getTotal())));
            meta.setLore(lore);
            info.setItemMeta(meta);
        }
        gui.setItem(SLOT_ITEM_INFO, info);

        gui.setItem(SLOT_BUY, createButton(Material.EMERALD_BLOCK, "&aBuy", Arrays.asList(
                "&7Click to purchase",
            "&7Amount: &e" + session.getAmount() + "x",
                messageUtil.color("&7Total: &a$" + messageUtil.money(session.getTotal()))
        )));

        player.openInventory(gui);
    }

    private boolean hasStackControls(ShopSession session) {
        return session.getItemStack().getMaxStackSize() > 1;
    }

    private ItemStack createButton(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(messageUtil.color(name));
            if (lore != null) {
                List<String> colorLore = new ArrayList<>();
                for (String line : lore) {
                    colorLore.add(messageUtil.color(line));
                }
                meta.setLore(colorLore);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "frameshop-button"), PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void attemptPurchase(Player player, ShopSession session) {
        Block signBlock = session.getSignLocation().getBlock();
        ItemFrame frame = getFrameAbove(signBlock);
        if (frame == null || frame.getItem() == null || frame.getItem().getType().isAir()) {
            player.sendMessage(messageUtil.prefixed("messages.item-frame-empty"));
            player.closeInventory();
            sessions.remove(player.getUniqueId());
            return;
        }

        ItemStack current = frame.getItem().clone();
        current.setAmount(1);

        Double baseWorth = plugin.getWorthService().getWorth(current);
        if (baseWorth == null || baseWorth <= 0.0D) {
            player.sendMessage(messageUtil.prefixed("messages.no-worth-found"));
            return;
        }

        double taxPercent = plugin.getConfig().getDouble("settings.tax-percent", 0.0D);
        double unitPrice = PriceUtil.applyTax(baseWorth, taxPercent);

        session.setItemStack(current);
        session.setUnitPrice(unitPrice);

        int amount = session.getAmount();
        if (amount <= 0) {
            player.sendMessage(messageUtil.prefixed("messages.invalid-amount"));
            return;
        }

        if (!InventoryUtil.canFitExactly(player.getInventory(), current, amount)) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("amount", String.valueOf(amount));
            placeholders.put("item", formatItemName(current));
            player.sendMessage(messageUtil.format("messages.not-enough-space", placeholders));
            return;
        }

        double total = unitPrice * amount;
        double balance = economy.getBalance(player);
        if (balance < total) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("total", messageUtil.money(total));
            placeholders.put("balance", messageUtil.money(balance));
            player.sendMessage(messageUtil.format("messages.not-enough-money", placeholders));
            return;
        }

        if (!economy.withdrawPlayer(player, total).transactionSuccess()) {
            player.sendMessage(messageUtil.prefixed("messages.purchase-failed"));
            return;
        }

        int remaining = amount;
        while (remaining > 0) {
            ItemStack toGive = current.clone();
            int stackAmount = Math.min(remaining, toGive.getMaxStackSize());
            toGive.setAmount(stackAmount);
            player.getInventory().addItem(toGive);
            remaining -= stackAmount;
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("amount", String.valueOf(amount));
        placeholders.put("item", formatItemName(current));
        placeholders.put("total", messageUtil.money(total));
        player.sendMessage(messageUtil.format("messages.buy-success", placeholders));
        player.closeInventory();
        sessions.remove(player.getUniqueId());
    }

    private String formatItemName(ItemStack itemStack) {
        String name = itemStack.getType().name().toLowerCase(Locale.ENGLISH).replace("_", " ");
        if (name.isEmpty()) {
            return "item";
        }
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private ItemFrame getFrameAbove(Block signBlock) {
        Location center = signBlock.getLocation().add(0.5, 2.0, 0.5);
        ItemFrame yPlusOne = null;
        ItemFrame yPlusTwo = null;

        for (Entity entity : signBlock.getWorld().getNearbyEntities(center, 0.55, 1.25, 0.55)) {
            if (entity instanceof ItemFrame) {
                ItemFrame frame = (ItemFrame) entity;
                if (frame.getLocation().getBlockX() != signBlock.getX()
                        || frame.getLocation().getBlockZ() != signBlock.getZ()) {
                    continue;
                }

                if (frame.getLocation().getBlockY() == signBlock.getY() + 1) {
                    yPlusOne = frame;
                } else if (frame.getLocation().getBlockY() == signBlock.getY() + 2) {
                    yPlusTwo = frame;
                }
            }
        }

        return yPlusOne != null ? yPlusOne : yPlusTwo;
    }

    private void refreshSignsFromFrame(ItemFrame frame) {
        List<Block> candidateSigns = Arrays.asList(
                frame.getLocation().getBlock().getRelative(BlockFace.DOWN),
                frame.getLocation().getBlock().getRelative(BlockFace.DOWN, 2)
        );

        for (Block signBlock : candidateSigns) {
            if (!(signBlock.getState() instanceof Sign)) {
                continue;
            }

            Sign sign = (Sign) signBlock.getState();
            if (!isShopSign(sign)) {
                continue;
            }

            ItemFrame linked = getFrameAbove(signBlock);
            if (linked == null || linked.getItem() == null || linked.getItem().getType().isAir()) {
                setSignInactive(sign);
                continue;
            }

            setSignActive(sign);
        }
    }

    private void deactivateSignsBelow(Location frameLocation) {
        List<Block> candidateSigns = Arrays.asList(
                frameLocation.getBlock().getRelative(BlockFace.DOWN),
                frameLocation.getBlock().getRelative(BlockFace.DOWN, 2)
        );

        for (Block signBlock : candidateSigns) {
            if (!(signBlock.getState() instanceof Sign)) {
                continue;
            }

            Sign sign = (Sign) signBlock.getState();
            if (!isShopSign(sign)) {
                continue;
            }

            setSignInactive(sign);
        }
    }

    private void setSignInactive(Sign sign) {
        String inactiveLabel = messageUtil.color("&c" + strip(getInactiveLabel()));
        if (sign.getLine(1).equals(inactiveLabel)) {
            return;
        }

        sign.setLine(1, inactiveLabel);
        sign.setGlowingText(true);
        sign.setColor(DyeColor.RED);
        sign.update(true, false);
    }

    private void setSignActive(Sign sign) {
        String activeLabel = messageUtil.color("&a" + strip(getBuyLabel()));
        if (sign.getLine(1).equals(activeLabel)) {
            return;
        }

        sign.setLine(1, activeLabel);
        sign.setGlowingText(true);
        sign.setColor(DyeColor.LIME);
        sign.update(true, false);
    }

    private boolean isShopSign(Sign sign) {
        String value = strip(sign.getLine(1));
        return value.equalsIgnoreCase(strip(getBuyLabel()))
                || value.equalsIgnoreCase(strip(getInactiveLabel()));
    }

    private String getBuyLabel() {
        return plugin.getConfig().getString("settings.sign-label", "[Buy]");
    }

    private String getInactiveLabel() {
        return plugin.getConfig().getString("settings.inactive-sign-label", "&c[Inactive]");
    }

    private String strip(String text) {
        return org.bukkit.ChatColor.stripColor(messageUtil.color(text == null ? "" : text));
    }
}
