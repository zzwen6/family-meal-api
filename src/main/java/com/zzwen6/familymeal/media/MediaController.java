package com.zzwen6.familymeal.media;

import com.zzwen6.familymeal.media.MediaDtos.MediaView;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/families/{familyId}/media")
public class MediaController {
    private final MediaService media;

    public MediaController(MediaService media) {
        this.media = media;
    }

    /** 管理员和掌厨上传本家庭菜谱的图片或短视频。 */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MediaView upload(@PathVariable Long familyId, @RequestParam("file") MultipartFile file) {
        return media.upload(familyId, file);
    }

    /**
     * 家庭成员（含儿童）可读。需登录：Authorization: Bearer，或查询参数 access_token
     *（给小程序 &lt;image&gt; / &lt;video&gt; 和 H5 的 src 用，不可猜测的文件名 + 登录校验）。
     */
    @GetMapping("/{storedName:.+}")
    public ResponseEntity<Resource> download(@PathVariable Long familyId, @PathVariable String storedName) {
        MediaService.Loaded loaded = media.download(familyId, storedName);
        MediaType type = MediaType.parseMediaType(loaded.file().getContentType());
        ContentDisposition cd = ContentDisposition.inline()
                .filename(loaded.file().getStoredName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=86400")
                .body(new FileSystemResource(loaded.path()));
    }
}
