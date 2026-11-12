package com.zzwen6.familymeal.family;

import com.zzwen6.familymeal.family.FamilyDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/families")
public class FamilyController {
    private final FamilyService service;

    public FamilyController(FamilyService service) {
        this.service = service;
    }

    @GetMapping
    public List<FamilyView> mine() { return service.mine(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FamilyView create(@Valid @RequestBody CreateFamilyRequest req) { return service.create(req); }

    @PostMapping("/join")
    public FamilyView join(@Valid @RequestBody JoinFamilyRequest req) { return service.join(req); }

    @GetMapping("/{familyId}")
    public FamilyView get(@PathVariable Long familyId) { return service.get(familyId); }

    @PutMapping("/{familyId}")
    public FamilyView update(@PathVariable Long familyId, @Valid @RequestBody UpdateFamilyRequest req) {
        return service.update(familyId, req);
    }

    @PostMapping("/{familyId}/invite-code/refresh")
    public FamilyView refreshInvite(@PathVariable Long familyId) { return service.refreshInviteCode(familyId); }

    @GetMapping("/{familyId}/members")
    public List<MemberView> members(@PathVariable Long familyId) { return service.members(familyId); }

    @PostMapping("/{familyId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public MemberView createMember(@PathVariable Long familyId, @Valid @RequestBody CreateMemberRequest req) {
        return service.createMember(familyId, req);
    }

    @PutMapping("/{familyId}/members/{memberId}")
    public MemberView updateMember(@PathVariable Long familyId, @PathVariable Long memberId,
                                   @Valid @RequestBody UpdateMemberRequest req) {
        return service.updateMember(familyId, memberId, req);
    }

    @DeleteMapping("/{familyId}/members/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable Long familyId, @PathVariable Long memberId) {
        service.removeMember(familyId, memberId);
    }
}
