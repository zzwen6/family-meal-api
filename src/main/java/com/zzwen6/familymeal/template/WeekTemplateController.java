package com.zzwen6.familymeal.template;

import com.zzwen6.familymeal.template.TemplateDtos.*;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/families/{familyId}")
public class WeekTemplateController {
    private final WeekTemplateService service;

    public WeekTemplateController(WeekTemplateService service) {
        this.service = service;
    }

    @GetMapping("/week-templates")
    public List<TemplateView> list(@PathVariable Long familyId) {
        return service.list(familyId);
    }

    @PostMapping("/week-templates")
    @ResponseStatus(HttpStatus.CREATED)
    public TemplateView create(@PathVariable Long familyId, @Valid @RequestBody TemplateRequest req) {
        return service.create(familyId, req);
    }

    @GetMapping("/week-templates/{templateId}")
    public TemplateView get(@PathVariable Long familyId, @PathVariable Long templateId) {
        return service.get(familyId, templateId);
    }

    @PutMapping("/week-templates/{templateId}")
    public TemplateView update(@PathVariable Long familyId, @PathVariable Long templateId,
                               @Valid @RequestBody TemplateRequest req) {
        return service.update(familyId, templateId, req);
    }

    @DeleteMapping("/week-templates/{templateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long familyId, @PathVariable Long templateId) {
        service.delete(familyId, templateId);
    }

    @PostMapping("/week-templates/from-week")
    @ResponseStatus(HttpStatus.CREATED)
    public TemplateView fromWeek(@PathVariable Long familyId, @Valid @RequestBody FromWeekRequest req) {
        return service.fromWeek(familyId, req);
    }

    @PostMapping("/week-templates/{templateId}/apply")
    public ApplyResult apply(@PathVariable Long familyId, @PathVariable Long templateId,
                             @Valid @RequestBody ApplyRequest req) {
        return service.applyToWeek(familyId, templateId, req);
    }

    @GetMapping("/meal-plans")
    public List<PlanItemView> plans(@PathVariable Long familyId,
                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.plans(familyId, from, to);
    }
}
