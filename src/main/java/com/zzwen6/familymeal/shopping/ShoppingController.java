package com.zzwen6.familymeal.shopping;

import com.zzwen6.familymeal.shopping.ShoppingDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/families/{familyId}")
public class ShoppingController {
    private final ShoppingService service;

    public ShoppingController(ShoppingService service) {
        this.service = service;
    }

    @GetMapping("/menus/{menuId}/shopping-list")
    public ShoppingListView get(@PathVariable Long familyId, @PathVariable Long menuId) {
        return service.get(familyId, menuId);
    }

    @PostMapping("/menus/{menuId}/shopping-list/regenerate")
    public ShoppingListView regenerate(@PathVariable Long familyId, @PathVariable Long menuId) {
        return service.regenerate(familyId, menuId);
    }

    @PostMapping("/menus/{menuId}/shopping-list/items")
    @ResponseStatus(HttpStatus.CREATED)
    public ItemView add(@PathVariable Long familyId, @PathVariable Long menuId, @Valid @RequestBody AddItemRequest req) {
        return service.addItem(familyId, menuId, req);
    }

    @PatchMapping("/shopping-items/{itemId}")
    public ItemView update(@PathVariable Long familyId, @PathVariable Long itemId, @RequestBody UpdateItemRequest req) {
        return service.updateItem(familyId, itemId, req);
    }

    @DeleteMapping("/shopping-items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long familyId, @PathVariable Long itemId) {
        service.deleteItem(familyId, itemId);
    }
}
