package com.zzwen6.familymeal.rating;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class RatingDtos {
    private RatingDtos() {}

    public record RatingRequest(
            @Min(value = 1, message = "请打 1～5 分") @Max(value = 5, message = "请打 1～5 分") int score,
            @Size(max = 255) String comment,
            Long memberId) {}

    public record RatingView(Long id, Long menuId, Long memberId, String memberName, int score, String comment,
                             boolean editable, LocalDateTime updatedAt) {}
}
