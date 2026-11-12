package com.zzwen6.familymeal.notify;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/families/{familyId}/notifications")
public class NotificationController {
    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public List<NotificationService.NotificationView> recent(@PathVariable Long familyId,
                                                             @RequestParam(defaultValue = "20") int limit) {
        return service.recent(familyId, limit);
    }
}
