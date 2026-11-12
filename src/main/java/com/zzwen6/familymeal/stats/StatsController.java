package com.zzwen6.familymeal.stats;

import com.zzwen6.familymeal.stats.StatsDtos.StatsView;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/families/{familyId}/stats")
public class StatsController {
    private final StatsService service;

    public StatsController(StatsService service) {
        this.service = service;
    }

    /** from/to 不传时默认本周一到周日。只统计本家庭日常菜单，不含聚餐。 */
    @GetMapping
    public StatsView stats(@PathVariable Long familyId,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.stats(familyId, from, to);
    }
}
