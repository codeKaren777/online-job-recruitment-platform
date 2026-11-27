package com.groupcc2.recruitment.profile.storage;

import com.groupcc2.recruitment.profile.service.ProfileFailure;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** Bounded allowlist for raster profile images; SVG and other active formats are rejected. */
@Component
public class ProfileImageValidator {
    public static final long MAX_BYTES = 2L * 1024 * 1024;

    public record Validated(Path path, String filename, String contentType, long size)
            implements AutoCloseable {
        @Override public void close() throws IOException { Files.deleteIfExists(path); }
    }

    public Validated validate(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES) {
            throw invalid(file != null && file.getSize() > MAX_BYTES);
        }
        String filename = file.getOriginalFilename();
        String contentType = file.getContentType();
        String extension = contentType == null ? "" : switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> "";
        };
        if (filename == null || filename.length() > 180 || extension.isEmpty()
                || !filename.matches("[A-Za-z0-9][A-Za-z0-9 _().-]*")
                || filename.contains("..")
                || !(contentType.equals("image/jpeg")
                    ? filename.toLowerCase(Locale.ROOT).endsWith(".jpg")
                        || filename.toLowerCase(Locale.ROOT).endsWith(".jpeg")
                    : filename.toLowerCase(Locale.ROOT).endsWith(extension))) {
            throw invalid(false);
        }

        Path temp = null;
        boolean success = false;
        try {
            temp = Files.createTempFile("profile-image-", extension);
            long size = 0;
            try (InputStream input = file.getInputStream(); OutputStream output = Files.newOutputStream(temp)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    size += count;
                    if (size > MAX_BYTES) throw invalid(true);
                    output.write(buffer, 0, count);
                }
            }
            if (size == 0 || !matchesSignature(temp, contentType)) throw invalid(false);
            success = true;
            return new Validated(temp, filename, contentType, size);
        } catch (IOException exception) {
            throw invalid(false);
        } finally {
            if (!success && temp != null) {
                try { Files.deleteIfExists(temp); } catch (IOException ignored) { }
            }
        }
    }

    private boolean matchesSignature(Path path, String type) throws IOException {
        byte[] header;
        try (InputStream input = Files.newInputStream(path)) {
            header = input.readNBytes(12);
        }
        return switch (type) {
            case "image/jpeg" -> header.length >= 3 && (header[0] & 0xff) == 0xff
                    && (header[1] & 0xff) == 0xd8 && (header[2] & 0xff) == 0xff;
            case "image/png" -> header.length >= 8 && (header[0] & 0xff) == 0x89
                    && header[1] == 'P' && header[2] == 'N' && header[3] == 'G'
                    && header[4] == 13 && header[5] == 10 && header[6] == 26 && header[7] == 10;
            case "image/webp" -> header.length >= 12 && ascii(header, 0, "RIFF")
                    && ascii(header, 8, "WEBP");
            default -> false;
        };
    }

    private static boolean ascii(byte[] bytes, int offset, String value) {
        for (int i = 0; i < value.length(); i++) if (bytes[offset + i] != value.charAt(i)) return false;
        return true;
    }

    private ProfileFailure invalid(boolean tooLarge) {
        return new ProfileFailure(tooLarge ? ProfileFailure.Code.FILE_TOO_LARGE : ProfileFailure.Code.INVALID_FILE);
    }
}
