package com.zzwen6.familymeal.media;

import com.zzwen6.familymeal.common.BizException;
import com.zzwen6.familymeal.common.TimeHolder;
import com.zzwen6.familymeal.config.AppProperties;
import com.zzwen6.familymeal.family.FamilyAccess;
import com.zzwen6.familymeal.media.MediaDtos.MediaView;
import com.zzwen6.familymeal.media.MediaTypes.Detected;
import com.zzwen6.familymeal.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 家庭菜谱媒体：默认存在可配置的本地目录（MEDIA_PATH），文件名用不可猜测的 UUID。
 * 未配置对象存储时走本地；本实现只做本地存储。
 */
@Service
public class MediaService {
    private static final Logger log = LoggerFactory.getLogger(MediaService.class);
    private static final Pattern OWN = Pattern.compile("/api/families/(\\d+)/media/([A-Za-z0-9._-]+)$");

    private final MediaFileRepository files;
    private final FamilyAccess access;
    private final AppProperties props;

    public MediaService(MediaFileRepository files, FamilyAccess access, AppProperties props) {
        this.files = files;
        this.access = access;
        this.props = props;
    }

    @Transactional
    public MediaView upload(Long familyId, MultipartFile file) {
        access.cook(familyId);
        if (file == null || file.isEmpty()) throw BizException.badRequest("请选择要上传的文件");
        byte[] head = readHead(file);
        Detected detected = MediaTypes.detect(head, file.getContentType(), file.getOriginalFilename());
        AppProperties.Media media = props.mediaOrDefault();
        long max = detected.kind() == MediaKind.IMAGE ? media.imageMaxBytes() : media.videoMaxBytes();
        if (file.getSize() > max) {
            String limit = detected.kind() == MediaKind.IMAGE ? "5MB" : "30MB";
            throw new BizException(HttpStatus.BAD_REQUEST, "FILE_TOO_LARGE",
                    (detected.kind() == MediaKind.IMAGE ? "图片" : "视频") + "不能超过 " + human(max) + "（建议上限 " + limit + "）");
        }
        String stored = UUID.randomUUID().toString().replace("-", "") + detected.extension();
        Path dest = dir().resolve(stored);
        try {
            Files.createDirectories(dest.getParent());
            file.transferTo(dest);
        } catch (IOException e) {
            log.warn("failed to store media {}", dest, e);
            throw new BizException(HttpStatus.INTERNAL_SERVER_ERROR, "MEDIA_STORE_FAILED", "保存文件失败，请稍后再试");
        }
        MediaFile row = new MediaFile();
        row.setFamilyId(familyId);
        row.setStoredName(stored);
        row.setOriginalName(trimName(file.getOriginalFilename()));
        row.setContentType(detected.contentType());
        row.setKind(detected.kind());
        row.setSizeBytes(file.getSize());
        row.setCreatedBy(CurrentUser.id());
        row.setCreatedAt(TimeHolder.now());
        files.save(row);
        return new MediaView(url(familyId, stored), stored, detected.contentType(), detected.kind(), file.getSize());
    }

    @Transactional(readOnly = true)
    public Loaded download(Long familyId, String storedName) {
        access.member(familyId);
        String name = sanitize(storedName);
        MediaFile row = files.findByFamilyIdAndStoredName(familyId, name)
                .orElseThrow(() -> BizException.notFound("文件"));
        Path path = dir().resolve(name);
        if (!Files.isRegularFile(path)) throw BizException.notFound("文件");
        return new Loaded(row, path);
    }

    /** 删除本家庭不再被菜谱引用的旧媒体文件。external / 他家庭的 URL 不会动。 */
    @Transactional
    public void deleteUnused(Long familyId, Collection<String> oldUrls, Collection<String> stillUsed) {
        if (oldUrls == null || oldUrls.isEmpty()) return;
        for (String url : oldUrls) {
            if (url == null || url.isBlank()) continue;
            if (stillUsed != null && stillUsed.stream().anyMatch(u -> u != null && u.equals(url))) continue;
            parseOwn(familyId, url).ifPresent(this::deleteStored);
        }
    }

    public void deleteStored(String storedName) {
        String name = sanitize(storedName);
        files.findByStoredName(name).ifPresent(row -> {
            files.delete(row);
            Path path = dir().resolve(name);
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                log.warn("failed to delete media file {}", path, e);
            }
        });
    }

    public static String url(Long familyId, String storedName) {
        return "/api/families/" + familyId + "/media/" + storedName;
    }

    public Optional<String> parseOwn(Long familyId, String url) {
        if (url == null || url.isBlank()) return Optional.empty();
        int q = url.indexOf('?');
        String path = q >= 0 ? url.substring(0, q) : url;
        int idx = path.indexOf("/api/families/");
        if (idx >= 0) path = path.substring(idx);
        Matcher m = OWN.matcher(path);
        if (!m.find()) return Optional.empty();
        if (!familyId.equals(Long.valueOf(m.group(1)))) return Optional.empty();
        return Optional.of(m.group(2));
    }

    private Path dir() {
        String raw = props.mediaOrDefault().path();
        Path p = Path.of(raw == null || raw.isBlank() ? "./data/media" : raw).toAbsolutePath().normalize();
        return p;
    }

    private static byte[] readHead(MultipartFile file) {
        byte[] buf = new byte[16];
        try (InputStream in = file.getInputStream()) {
            int n = in.read(buf);
            if (n <= 0) throw BizException.badRequest("文件是空的");
            if (n < buf.length) {
                byte[] slim = new byte[n];
                System.arraycopy(buf, 0, slim, 0, n);
                return slim;
            }
            return buf;
        } catch (IOException e) {
            throw BizException.badRequest("无法读取上传的文件");
        }
    }

    private static String sanitize(String storedName) {
        if (storedName == null || storedName.isBlank() || storedName.contains("..")
                || storedName.contains("/") || storedName.contains("\\")) {
            throw BizException.notFound("文件");
        }
        return storedName;
    }

    private static String trimName(String name) {
        if (name == null || name.isBlank()) return null;
        String n = Path.of(name).getFileName().toString();
        return n.length() > 255 ? n.substring(n.length() - 255) : n;
    }

    private static String human(long bytes) {
        if (bytes >= 1024 * 1024) return (bytes / (1024 * 1024)) + "MB";
        if (bytes >= 1024) return (bytes / 1024) + "KB";
        return bytes + "B";
    }

    public record Loaded(MediaFile file, Path path) {}
}
