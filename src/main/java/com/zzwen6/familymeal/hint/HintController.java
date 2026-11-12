package com.zzwen6.familymeal.hint;

import com.zzwen6.familymeal.common.Enums.MealType;
import com.zzwen6.familymeal.hint.HintDtos.HintView;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/families/{familyId}/hints")
public class HintController {
    private final MealHintService hints;

    public HintController(MealHintService hints) {
        this.hints = hints;
    }

    /** 吃腻 / 公平 / 剩菜提示；可带 dishId 或 name 只看某一道菜（点意愿页用）。 */
    @GetMapping
    public List<HintView> list(@PathVariable Long familyId,
                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                               @RequestParam MealType mealType,
                               @RequestParam(required = false) Long dishId,
                               @RequestParam(required = false) String name) {
        return hints.list(familyId, date, mealType, dishId, name);
    }
}
