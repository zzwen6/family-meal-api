package com.zzwen6.familymeal.inventory;

import com.zzwen6.familymeal.inventory.InventoryDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/families/{familyId}")
public class InventoryController {
    private final InventoryService inventory;
    private final LeftoverService leftovers;

    public InventoryController(InventoryService inventory, LeftoverService leftovers) {
        this.inventory = inventory;
        this.leftovers = leftovers;
    }

    @GetMapping("/inventory")
    public List<InventoryView> inventory(@PathVariable Long familyId) {
        return inventory.list(familyId);
    }

    @GetMapping("/inventory/ledger")
    public List<LedgerView> ledger(@PathVariable Long familyId,
                                   @RequestParam(defaultValue = "20") int limit) {
        return inventory.ledger(familyId, limit);
    }

    @PostMapping("/inventory")
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryView createInventory(@PathVariable Long familyId, @Valid @RequestBody InventoryRequest req) {
        return inventory.create(familyId, req);
    }

    @PutMapping("/inventory/{itemId}")
    public InventoryView updateInventory(@PathVariable Long familyId, @PathVariable Long itemId,
                                         @Valid @RequestBody InventoryRequest req) {
        return inventory.update(familyId, itemId, req);
    }

    @DeleteMapping("/inventory/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteInventory(@PathVariable Long familyId, @PathVariable Long itemId) {
        inventory.delete(familyId, itemId);
    }

    @GetMapping("/leftovers")
    public List<LeftoverView> leftovers(@PathVariable Long familyId,
                                        @RequestParam(required = false) Boolean used) {
        return leftovers.list(familyId, used);
    }

    @PostMapping("/leftovers")
    @ResponseStatus(HttpStatus.CREATED)
    public LeftoverView createLeftover(@PathVariable Long familyId, @Valid @RequestBody LeftoverRequest req) {
        return leftovers.create(familyId, req);
    }

    @PutMapping("/leftovers/{leftoverId}")
    public LeftoverView updateLeftover(@PathVariable Long familyId, @PathVariable Long leftoverId,
                                       @Valid @RequestBody LeftoverRequest req) {
        return leftovers.update(familyId, leftoverId, req);
    }

    @PostMapping("/leftovers/{leftoverId}/use")
    public LeftoverView useLeftover(@PathVariable Long familyId, @PathVariable Long leftoverId,
                                    @RequestBody(required = false) UseBody body) {
        return leftovers.markUsed(familyId, leftoverId, body == null ? null : body.menuId());
    }

    @DeleteMapping("/leftovers/{leftoverId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLeftover(@PathVariable Long familyId, @PathVariable Long leftoverId) {
        leftovers.delete(familyId, leftoverId);
    }

    public record UseBody(Long menuId) {}
}
