package com.whiteiverson.minecraft.frame_shop.Utilities;

public final class PriceUtil {
    private PriceUtil() {
    }

    public static double applyTax(double basePrice, double taxPercent) {
        return basePrice * (1.0D + (taxPercent / 100.0D));
    }
}
