package com.whiteiverson.minecraft.frame_shop.Utilities;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class InventoryUtil {
    private InventoryUtil() {
    }

    public static boolean canFitExactly(Inventory inventory, ItemStack source, int amount) {
        if (amount <= 0) {
            return false;
        }

        ItemStack probe = source.clone();
        int remaining = amount;

        for (ItemStack content : inventory.getStorageContents()) {
            if (content == null || content.getType().isAir()) {
                continue;
            }

            if (!content.isSimilar(probe)) {
                continue;
            }

            int freeInStack = content.getMaxStackSize() - content.getAmount();
            if (freeInStack > 0) {
                remaining -= freeInStack;
                if (remaining <= 0) {
                    return true;
                }
            }
        }

        for (ItemStack content : inventory.getStorageContents()) {
            if (content != null && !content.getType().isAir()) {
                continue;
            }

            remaining -= probe.getMaxStackSize();
            if (remaining <= 0) {
                return true;
            }
        }

        return false;
    }
}
