package com.zzwen6.familymeal.rotation;

import com.zzwen6.familymeal.rotation.CookRotationDtos.*;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/families/{familyId}")
public class CookRotationController {
    private final CookRotationService service;

    public CookRotationController(CookRotationService service) {
        this.service = service;
    }

    @GetMapping("/cook-rotation")
    public RotationView get(@PathVariable Long familyId) {
        return service.get(familyId);
    }

    @PutMapping("/cook-rotation")
    public RotationView update(@PathVariable Long familyId, @RequestBody UpdateRotationRequest req) {
        return service.update(familyId, req);
    }

    @GetMapping("/cook-assignments")
    public List<AssignmentView> list(@PathVariable Long familyId,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.list(familyId, from, to);
    }

    @PutMapping("/cook-assignments")
    public AssignmentView assign(@PathVariable Long familyId, @Valid @RequestBody AssignRequest req) {
        return service.assign(familyId, req);
    }

    @PostMapping("/cook-assignments/generate")
    public List<AssignmentView> generate(@PathVariable Long familyId,
                                         @RequestBody(required = false) GenerateRequest req) {
        return service.generate(familyId, req == null ? new GenerateRequest(null, null, null) : req);
    }

    @PostMapping("/cook-assignments/swap")
    public List<AssignmentView> swap(@PathVariable Long familyId, @Valid @RequestBody SwapRequest req) {
        return service.swap(familyId, req);
    }
}
