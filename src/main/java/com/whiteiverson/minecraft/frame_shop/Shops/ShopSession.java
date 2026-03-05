package com.whiteiverson.minecraft.frame_shop.Shops;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

public class ShopSession {
    private final Location signLocation;
    private ItemStack itemStack;
    private double unitPrice;
    private int amount;

    public ShopSession(Location signLocation, ItemStack itemStack, double unitPrice, int amount) {
        this.signLocation = signLocation;
        this.itemStack = itemStack;
        this.unitPrice = unitPrice;
        this.amount = amount;
    }

    public Location getSignLocation() {
        return signLocation;
    }

    public ItemStack getItemStack() {
        return itemStack;
    }

    public void setItemStack(ItemStack itemStack) {
        this.itemStack = itemStack;
    }

    public double getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(double unitPrice) {
        this.unitPrice = unitPrice;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    public double getTotal() {
        return unitPrice * amount;
    }
}
