package com.zzwen6.familymeal.rating;

import com.zzwen6.familymeal.rating.RatingDtos.RatingRequest;
import com.zzwen6.familymeal.rating.RatingDtos.RatingView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/families/{familyId}/menus/{menuId}/ratings")
public class MealRatingController {
    private final MealRatingService service;

    public MealRatingController(MealRatingService service) {
        this.service = service;
    }

    @GetMapping
    public List<RatingView> list(@PathVariable Long familyId, @PathVariable Long menuId) {
        return service.list(familyId, menuId);
    }

    @PostMapping
    public RatingView upsert(@PathVariable Long familyId, @PathVariable Long menuId,
                             @Valid @RequestBody RatingRequest req) {
        return service.upsert(familyId, menuId, req);
    }
}
