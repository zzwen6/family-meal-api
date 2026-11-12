package com.zzwen6.familymeal.nutrition;

import java.math.BigDecimal;
import java.util.List;

/** 食材营养参考：每 100 克可食部的近似值，可选单件克数。 */
public record FoodEntry(String name, List<String> aliases, BigDecimal kcal, BigDecimal protein,
                        BigDecimal fat, BigDecimal carb, BigDecimal pieceGrams, boolean zeroEnergy) {}
