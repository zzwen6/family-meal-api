package com.zzwen6.familymeal.media;

import com.zzwen6.familymeal.common.BizException;

import java.util.Locale;
import java.util.Set;

/** 根据魔数和声明的 MIME 判定图片/短视频类型，不信任客户端文件名。 */
public final class MediaTypes {
    private MediaTypes() {}

    public record Detected(MediaKind kind, String contentType, String extension) {}

    private static final Set<String> IMAGE_CT = Set.of("image/jpeg", "image/jpg", "image/png", "image/webp", "image/gif");
    private static final Set<String> VIDEO_CT = Set.of("video/mp4", "video/webm", "video/quicktime");

    public static Detected detect(byte[] head, String declaredType, String filename) {
        Detected byMagic = fromMagic(head);
        if (byMagic != null) return byMagic;
        String ct = declaredType == null ? "" : declaredType.trim().toLowerCase(Locale.ROOT);
        int semi = ct.indexOf(';');
        if (semi > 0) ct = ct.substring(0, semi).trim();
        if ("image/jpg".equals(ct)) ct = "image/jpeg";
        if (IMAGE_CT.contains(ct)) {
            return new Detected(MediaKind.IMAGE, "image/jpg".equals(declaredType) ? "image/jpeg" : ct, extFor(ct));
        }
        if (VIDEO_CT.contains(ct)) {
            return new Detected(MediaKind.VIDEO, "video/quicktime".equals(ct) ? "video/mp4" : ct,
                    "video/webm".equals(ct) ? ".webm" : ".mp4");
        }
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return new Detected(MediaKind.IMAGE, "image/jpeg", ".jpg");
        if (name.endsWith(".png")) return new Detected(MediaKind.IMAGE, "image/png", ".png");
        if (name.endsWith(".webp")) return new Detected(MediaKind.IMAGE, "image/webp", ".webp");
        if (name.endsWith(".gif")) return new Detected(MediaKind.IMAGE, "image/gif", ".gif");
        if (name.endsWith(".mp4") || name.endsWith(".mov")) return new Detected(MediaKind.VIDEO, "video/mp4", ".mp4");
        if (name.endsWith(".webm")) return new Detected(MediaKind.VIDEO, "video/webm", ".webm");
        throw BizException.badRequest("只支持 jpeg / png / webp / gif 图片和 mp4 / webm 短视频");
    }

    private static Detected fromMagic(byte[] b) {
        if (b == null || b.length < 12) return null;
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8) return new Detected(MediaKind.IMAGE, "image/jpeg", ".jpg");
        if ((b[0] & 0xFF) == 0x89 && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47)
            return new Detected(MediaKind.IMAGE, "image/png", ".png");
        if (b[0] == 0x47 && b[1] == 0x49 && b[2] == 0x46 && b[3] == 0x38)
            return new Detected(MediaKind.IMAGE, "image/gif", ".gif");
        if (b[0] == 0x52 && b[1] == 0x49 && b[2] == 0x46 && b[3] == 0x46
                && b[8] == 0x57 && b[9] == 0x45 && b[10] == 0x42 && b[11] == 0x50)
            return new Detected(MediaKind.IMAGE, "image/webp", ".webp");
        if ((b[0] & 0xFF) == 0x1A && (b[1] & 0xFF) == 0x45 && (b[2] & 0xFF) == 0xDF && (b[3] & 0xFF) == 0xA3)
            return new Detected(MediaKind.VIDEO, "video/webm", ".webm");
        // ISO BMFF: size + "ftyp"
        if (b[4] == 0x66 && b[5] == 0x74 && b[6] == 0x79 && b[7] == 0x70)
            return new Detected(MediaKind.VIDEO, "video/mp4", ".mp4");
        return null;
    }

    private static String extFor(String ct) {
        return switch (ct) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> ".jpg";
        };
    }
}
