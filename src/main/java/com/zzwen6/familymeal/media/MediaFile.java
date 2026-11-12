package com.zzwen6.familymeal.media;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "media_file")
public class MediaFile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long familyId;
    @Column(nullable = false, length = 80, unique = true)
    private String storedName;
    private String originalName;
    @Column(nullable = false, length = 128)
    private String contentType;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MediaKind kind;
    @Column(nullable = false)
    private long sizeBytes;
    private Long createdBy;
    @Column(nullable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public Long getFamilyId() { return familyId; }
    public void setFamilyId(Long familyId) { this.familyId = familyId; }
    public String getStoredName() { return storedName; }
    public void setStoredName(String storedName) { this.storedName = storedName; }
    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public MediaKind getKind() { return kind; }
    public void setKind(MediaKind kind) { this.kind = kind; }
    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
