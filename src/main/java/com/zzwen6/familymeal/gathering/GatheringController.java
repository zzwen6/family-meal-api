package com.zzwen6.familymeal.gathering;

import com.zzwen6.familymeal.gathering.GatheringDtos.*;
import com.zzwen6.familymeal.notify.NotificationService.NotificationView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 临时聚餐接口，独立于 /api/families/{id}。全部需要登录（SecurityConfig 里 /api/** authenticated），
 * 详情等只对发起人和本场参与者开放，见 {@link GatheringAccess}。
 */
@RestController
@RequestMapping("/api/gatherings")
public class GatheringController {
    private final GatheringService service;

    public GatheringController(GatheringService service) {
        this.service = service;
    }

    @GetMapping
    public List<GatheringSummary> mine() { return service.mine(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GatheringDetail create(@Valid @RequestBody CreateRequest req) { return service.create(req); }

    /** 邀请预览（已登录用户凭 token 看标题、时间、主人昵称，决定是否加入）。 */
    @GetMapping("/invite/{token}")
    public InvitePreview preview(@PathVariable String token) { return service.preview(token); }

    @PostMapping("/invite/{token}/join")
    public GatheringDetail join(@PathVariable String token, @Valid @RequestBody(required = false) JoinRequest req) {
        return service.join(token, req);
    }

    @GetMapping("/{id}")
    public GatheringDetail get(@PathVariable Long id) { return service.get(id); }

    @PutMapping("/{id}")
    public GatheringDetail update(@PathVariable Long id, @Valid @RequestBody UpdateRequest req) { return service.update(id, req); }

    /** 复用这波人：从这场新建一场（参与者拷贝，RSVP 置为 MAYBE）。 */
    @PostMapping("/{id}/copy")
    @ResponseStatus(HttpStatus.CREATED)
    public GatheringDetail copy(@PathVariable Long id, @Valid @RequestBody CopyRequest req) { return service.copy(id, req); }

    @PutMapping("/{id}/me")
    public GatheringDetail rsvp(@PathVariable Long id, @Valid @RequestBody RsvpRequest req) { return service.updateRsvp(id, req); }

    @PostMapping("/{id}/participants")
    @ResponseStatus(HttpStatus.CREATED)
    public GatheringDetail addGuest(@PathVariable Long id, @Valid @RequestBody AddGuestRequest req) { return service.addGuest(id, req); }

    @DeleteMapping("/{id}/participants/{participantId}")
    public GatheringDetail removeParticipant(@PathVariable Long id, @PathVariable Long participantId) {
        return service.removeParticipant(id, participantId);
    }

    /** 主人掌厨：想吃；众筹投票：提议（自动投自己一票）；各带一道：认领。 */
    @PostMapping("/{id}/wishes")
    @ResponseStatus(HttpStatus.CREATED)
    public GatheringDetail addWish(@PathVariable Long id, @Valid @RequestBody WishRequest req) { return service.addWish(id, req); }

    @PutMapping("/{id}/wishes/{wishId}")
    public GatheringDetail updateWish(@PathVariable Long id, @PathVariable Long wishId, @Valid @RequestBody WishRequest req) {
        return service.updateWish(id, wishId, req);
    }

    @DeleteMapping("/{id}/wishes/{wishId}")
    public GatheringDetail deleteWish(@PathVariable Long id, @PathVariable Long wishId) { return service.deleteWish(id, wishId); }

    @PostMapping("/{id}/wishes/{wishId}/vote")
    public GatheringDetail vote(@PathVariable Long id, @PathVariable Long wishId) { return service.vote(id, wishId); }

    @DeleteMapping("/{id}/wishes/{wishId}/vote")
    public GatheringDetail unvote(@PathVariable Long id, @PathVariable Long wishId) { return service.unvote(id, wishId); }

    @PostMapping("/{id}/confirm")
    public GatheringDetail confirm(@PathVariable Long id, @Valid @RequestBody ConfirmRequest req) { return service.confirm(id, req); }

    @PostMapping("/{id}/reopen")
    public GatheringDetail reopen(@PathVariable Long id, @RequestBody(required = false) ReopenRequest req) {
        return service.reopen(id, req);
    }

    @PostMapping("/{id}/ready")
    public GatheringDetail ready(@PathVariable Long id) { return service.ready(id); }

    @PostMapping("/{id}/close")
    public GatheringDetail close(@PathVariable Long id) { return service.close(id); }

    @GetMapping("/{id}/notifications")
    public List<NotificationView> notifications(@PathVariable Long id, @RequestParam(defaultValue = "20") int limit) {
        return service.notifications(id, limit);
    }

    @GetMapping("/{id}/shopping")
    public ShoppingView shopping(@PathVariable Long id) { return service.shopping(id); }

    @PostMapping("/{id}/shopping/items")
    @ResponseStatus(HttpStatus.CREATED)
    public ShoppingView addShoppingItem(@PathVariable Long id, @Valid @RequestBody ShoppingItemRequest req) {
        return service.addShoppingItem(id, req);
    }

    @PutMapping("/{id}/shopping/items/{itemId}")
    public ShoppingView updateShoppingItem(@PathVariable Long id, @PathVariable Long itemId,
                                           @Valid @RequestBody ShoppingItemRequest req) {
        return service.updateShoppingItem(id, itemId, req);
    }

    @PatchMapping("/{id}/shopping/items/{itemId}")
    public ShoppingView markPurchased(@PathVariable Long id, @PathVariable Long itemId,
                                      @Valid @RequestBody PurchasedRequest req) {
        return service.markPurchased(id, itemId, req);
    }

    @DeleteMapping("/{id}/shopping/items/{itemId}")
    public ShoppingView deleteShoppingItem(@PathVariable Long id, @PathVariable Long itemId) {
        return service.deleteShoppingItem(id, itemId);
    }
}
