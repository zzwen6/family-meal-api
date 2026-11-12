package com.zzwen6.familymeal.dish;

import com.zzwen6.familymeal.dish.DishDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/families/{familyId}/dishes")
public class DishController {
    private final DishService service;

    public DishController(DishService service) {
        this.service = service;
    }

    @GetMapping
    public List<DishSummary> list(@PathVariable Long familyId,
                                  @RequestParam(required = false) String category,
                                  @RequestParam(required = false) String keyword,
                                  @RequestParam(defaultValue = "false") boolean includeInactive) {
        return service.list(familyId, category, keyword, includeInactive);
    }

    @GetMapping("/categories")
    public List<String> categories(@PathVariable Long familyId) { return service.categories(familyId); }

    /** servings 传了就按份数换算用量。 */
    @GetMapping("/{dishId}")
    public DishDetail detail(@PathVariable Long familyId, @PathVariable Long dishId,
                             @RequestParam(required = false) Integer servings) {
        return service.detail(familyId, dishId, servings);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DishDetail create(@PathVariable Long familyId, @Valid @RequestBody DishRequest req) {
        return service.create(familyId, req);
    }

    @PutMapping("/{dishId}")
    public DishDetail update(@PathVariable Long familyId, @PathVariable Long dishId, @Valid @RequestBody DishRequest req) {
        return service.update(familyId, dishId, req);
    }

    @DeleteMapping("/{dishId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable Long familyId, @PathVariable Long dishId) {
        service.setActive(familyId, dishId, false);
    }

    @PostMapping("/{dishId}/restore")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restore(@PathVariable Long familyId, @PathVariable Long dishId) {
        service.setActive(familyId, dishId, true);
    }
}
