package com.zzwen6.familymeal.inventory;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class InventoryDtos {
    private InventoryDtos() {}

    public record InventoryView(Long id, String name, BigDecimal amount, String unit, boolean staple, String note) {
        public static InventoryView of(InventoryItem i) {
            return new InventoryView(i.getId(), i.getName(), i.getAmount(), i.getUnit(), i.isStaple(), i.getNote());
        }
    }

    public record InventoryRequest(
            @NotBlank(message = "名称不能为空") @Size(max = 64) String name,
            @DecimalMin("0") BigDecimal amount,
            @Size(max = 16) String unit,
            Boolean staple,
            @Size(max = 255) String note) {}

    public record LeftoverView(Long id, Long menuId, String name, BigDecimal amount, String unit,
                               LocalDate expiresOn, boolean used, LocalDateTime usedAt, Long usedMenuId, String note) {
        public static LeftoverView of(Leftover l) {
            return new LeftoverView(l.getId(), l.getMenuId(), l.getName(), l.getAmount(), l.getUnit(),
                    l.getExpiresOn(), l.isUsed(), l.getUsedAt(), l.getUsedMenuId(), l.getNote());
        }
    }

    public record LeftoverRequest(
            Long menuId,
            @NotBlank(message = "名称不能为空") @Size(max = 64) String name,
            @DecimalMin("0") BigDecimal amount,
            @Size(max = 16) String unit,
            LocalDate expiresOn,
            @Size(max = 255) String note) {}

    public record LedgerView(Long id, Long inventoryItemId, String sourceType, Long sourceId, String name,
                             String unit, java.math.BigDecimal delta, java.math.BigDecimal balanceAfter,
                             String note, java.time.LocalDateTime createdAt) {
        public static LedgerView of(InventoryLedger l) {
            return new LedgerView(l.getId(), l.getInventoryItemId(), l.getSourceType().name(), l.getSourceId(),
                    l.getName(), l.getUnit(), l.getDelta(), l.getBalanceAfter(), l.getNote(), l.getCreatedAt());
        }
    }
}
