package com.zzwen6.familymeal.gathering;

import com.zzwen6.familymeal.family.FamilyAccess;
import com.zzwen6.familymeal.gathering.GatheringDtos.FamilySlotView;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 家庭日历占位：家庭成员能看到「这天这餐有一场临时聚餐」（标题、发起人），避免和家里的饭冲突。
 * 只返回占位信息；要看聚餐详情仍须是那场的参与者。
 */
@RestController
@RequestMapping("/api/families/{familyId}/gatherings")
public class FamilyGatheringController {
    private final FamilyAccess access;
    private final GatheringService service;

    public FamilyGatheringController(FamilyAccess access, GatheringService service) {
        this.access = access;
        this.service = service;
    }

    @GetMapping
    public List<FamilySlotView> slots(@PathVariable Long familyId,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        access.member(familyId);
        return service.familySlots(familyId, from, to);
    }
}
