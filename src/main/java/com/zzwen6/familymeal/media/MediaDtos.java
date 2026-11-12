package com.zzwen6.familymeal.media;

public final class MediaDtos {
    private MediaDtos() {}

    public record MediaView(String url, String storedName, String contentType, MediaKind kind, long sizeBytes) {}
}
