package com.groupcc2.recruitment.profile.storage;

import com.groupcc2.recruitment.profile.service.ProfileFailure;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** Validates bounded JPEG/PNG image data by decoding it before storage. */
@Component
public class ProfilePictureValidator {
    public static final long DEFAULT_MAX_BYTES = 5L * 1024 * 1024;
    public static final int MAX_WIDTH = 2048;
    public static final int MAX_HEIGHT = 2048;
    private static final Logger log = LoggerFactory.getLogger(ProfilePictureValidator.class);
    private final long maxBytes;

    public record Validated(Path path, String extension, String contentType, long size) implements AutoCloseable {
        @Override public void close() throws IOException { Files.deleteIfExists(path); }
    }

    public ProfilePictureValidator(@Value("${app.profile.picture.max-bytes:5242880}") long maxBytes) {
        if (maxBytes < 1 || maxBytes > DEFAULT_MAX_BYTES) {
            throw new IllegalArgumentException("Profile picture size limit must be between 1 and 5242880 bytes");
        }
        this.maxBytes = maxBytes;
    }

    public Validated validate(MultipartFile file) {
        if (file == null || file.isEmpty()) throw invalid();
        if (file.getSize() > maxBytes) throw tooLarge();
        String filename = file.getOriginalFilename();
        if (filename == null || filename.length() > 180 || filename.contains("..")) throw invalid();
        String lowerName = filename.toLowerCase(Locale.ROOT);
        String extension;
        String contentType;
        if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) {
            extension = "jpg";
            contentType = "image/jpeg";
        } else if (lowerName.endsWith(".png")) {
            extension = "png";
            contentType = "image/png";
        } else {
            throw invalid();
        }
        if (!contentType.equals(file.getContentType())) throw invalid();

        Path temp = null;
        boolean valid = false;
        try {
            temp = Files.createTempFile("profile-picture-", "." + extension);
            long size = 0;
            try (InputStream input = file.getInputStream(); OutputStream output = Files.newOutputStream(temp)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    size += count;
                    if (size > maxBytes) throw tooLarge();
                    output.write(buffer, 0, count);
                }
            }
            if (size == 0) throw invalid();
            validateImage(temp, contentType);
            valid = true;
            return new Validated(temp, extension, contentType, size);
        } catch (ProfileFailure failure) {
            throw failure;
        } catch (IOException | RuntimeException failure) {
            throw invalid();
        } finally {
            if (!valid && temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException cleanupFailure) {
                    log.warn("Failed to remove rejected profile picture temporary file");
                }
            }
        }
    }

    private static void validateImage(Path path, String expectedContentType) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(path.toFile())) {
            if (input == null) throw invalid();
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalid();
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                String expectedFormat = expectedContentType.equals("image/jpeg") ? "jpeg" : "png";
                if (!format.equals(expectedFormat) && !(expectedFormat.equals("jpeg") && format.equals("jpg"))) {
                    throw invalid();
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > MAX_WIDTH || height > MAX_HEIGHT) throw invalid();
                BufferedImage decoded = reader.read(0);
                if (decoded == null || decoded.getWidth() != width || decoded.getHeight() != height) throw invalid();
            } finally {
                reader.dispose();
            }
        }
    }

    private static ProfileFailure invalid() { return new ProfileFailure(ProfileFailure.Code.INVALID_FILE); }
    private static ProfileFailure tooLarge() { return new ProfileFailure(ProfileFailure.Code.FILE_TOO_LARGE); }
}
