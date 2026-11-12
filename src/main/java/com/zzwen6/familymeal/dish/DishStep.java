package com.zzwen6.familymeal.dish;

import jakarta.persistence.*;

@Entity
@Table(name = "dish_step")
public class DishStep {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dish_id")
    private Dish dish;
    @Column(nullable = false)
    private int stepNo;
    @Column(nullable = false, length = 1000)
    private String content;
    private Integer durationMinutes;
    private String imageUrl;
    private String videoUrl;
    private String videoCoverUrl;
    private Integer videoDurationSeconds;

    public Long getId() { return id; }
    public Dish getDish() { return dish; }
    public void setDish(Dish dish) { this.dish = dish; }
    public int getStepNo() { return stepNo; }
    public void setStepNo(int stepNo) { this.stepNo = stepNo; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }
    public String getVideoCoverUrl() { return videoCoverUrl; }
    public void setVideoCoverUrl(String videoCoverUrl) { this.videoCoverUrl = videoCoverUrl; }
    public Integer getVideoDurationSeconds() { return videoDurationSeconds; }
    public void setVideoDurationSeconds(Integer videoDurationSeconds) { this.videoDurationSeconds = videoDurationSeconds; }
}
