package com.zzwen6.familymeal.menu;

import com.zzwen6.familymeal.common.Enums.MealType;
import com.zzwen6.familymeal.menu.MenuDtos.*;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/families/{familyId}/menus")
public class MenuController {
    private final MenuService service;

    public MenuController(MenuService service) {
        this.service = service;
    }

    /** 某天某餐的概览：全员意愿、谁没点、谁不在家、按菜汇总、已确认菜单。 */
    @GetMapping("/board")
    public BoardView board(@PathVariable Long familyId,
                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                           @RequestParam MealType mealType) {
        return service.board(familyId, date, mealType);
    }

    /** 日历：一段时间内每餐的菜单状态。 */
    @GetMapping
    public List<MenuView> calendar(@PathVariable Long familyId,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.calendar(familyId, from, to);
    }

    @GetMapping("/{menuId}")
    public MenuView get(@PathVariable Long familyId, @PathVariable Long menuId) {
        return service.get(familyId, menuId);
    }

    @PostMapping("/confirm")
    public ConfirmResponse confirm(@PathVariable Long familyId, @Valid @RequestBody ConfirmRequest req) {
        return service.confirm(familyId, req);
    }

    @PostMapping("/{menuId}/lock")
    public MenuView lock(@PathVariable Long familyId, @PathVariable Long menuId) {
        return service.lock(familyId, menuId);
    }

    @PostMapping("/{menuId}/reopen")
    public MenuView reopen(@PathVariable Long familyId, @PathVariable Long menuId,
                           @RequestBody(required = false) ReopenRequest req) {
        return service.reopen(familyId, menuId, req);
    }

    @PostMapping("/reopen")
    public MenuView reopenSlot(@PathVariable Long familyId, @Valid @RequestBody ReopenSlotRequest req) {
        return service.reopenSlot(familyId, req);
    }

    /** 开饭通知钩子。 */
    @PostMapping("/{menuId}/ready")
    public MenuView ready(@PathVariable Long familyId, @PathVariable Long menuId) {
        return service.ready(familyId, menuId);
    }
}
