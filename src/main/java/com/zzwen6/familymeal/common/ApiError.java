package com.zzwen6.familymeal.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/** 统一错误格式：{ code, message, status, path, timestamp, fieldErrors? } */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(String code, String message, int status, String path, Instant timestamp,
                       Map<String, String> fieldErrors) {
    public static ApiError of(int status, String code, String message, String path) {
        return new ApiError(code, message, status, path, Instant.now(), null);
    }
}
