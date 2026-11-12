package com.zzwen6.familymeal.wish;

import com.zzwen6.familymeal.common.Enums.MealType;
import com.zzwen6.familymeal.wish.WishDtos.*;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/families/{familyId}/wishes")
public class WishController {
    private final WishService service;

    public WishController(WishService service) {
        this.service = service;
    }

    /** 某天某餐全家的意愿。 */
    @GetMapping
    public List<WishView> list(@PathVariable Long familyId,
                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                               @RequestParam MealType mealType) {
        return service.list(familyId, date, mealType);
    }

    /** 我在一段日期内的意愿（默认今天起 7 天）。 */
    @GetMapping("/mine")
    public List<WishView> mine(@PathVariable Long familyId,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                               @RequestParam(required = false) Long memberId) {
        return service.mine(familyId, from, to, memberId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WishView create(@PathVariable Long familyId, @Valid @RequestBody WishRequest req) {
        return service.create(familyId, req);
    }

    @PutMapping("/{wishId}")
    public WishView update(@PathVariable Long familyId, @PathVariable Long wishId, @Valid @RequestBody WishRequest req) {
        return service.update(familyId, wishId, req);
    }

    @DeleteMapping("/{wishId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long familyId, @PathVariable Long wishId) {
        service.delete(familyId, wishId);
    }
}
