package com.groupcc2.recruitment.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.groupcc2.recruitment.profile.service.ProfileFailure;
import com.groupcc2.recruitment.profile.storage.ProfilePictureValidator;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ProfilePictureValidatorTest {
    private final ProfilePictureValidator validator =
            new ProfilePictureValidator(ProfilePictureValidator.DEFAULT_MAX_BYTES);

    @Test
    void acceptsDecodableJpegAndPngContent() throws Exception {
        try (var png = validator.validate(image("png", "image/png", "avatar.png", 24, 12));
             var jpeg = validator.validate(image("jpeg", "image/jpeg", "avatar.jpg", 12, 24))) {
            assertThat(png.contentType()).isEqualTo("image/png");
            assertThat(png.extension()).isEqualTo("png");
            assertThat(jpeg.contentType()).isEqualTo("image/jpeg");
            assertThat(jpeg.extension()).isEqualTo("jpg");
        }
    }

    @Test
    void rejectsUnsupportedExtensionsAndDeclaredMimeMismatch() throws Exception {
        MockMultipartFile unsupported = new MockMultipartFile(
                "file", "avatar.svg", "image/svg+xml", "<svg/>".getBytes());
        assertInvalid(unsupported);
        assertInvalid(image("png", "image/jpeg", "avatar.png", 2, 2));
    }

    @Test
    void rejectsCorruptImageBytesEvenWithPlausibleNameAndMime() {
        assertInvalid(new MockMultipartFile("file", "avatar.png", "image/png", new byte[] {1, 2, 3, 4}));
    }

    @Test
    void rejectsImagesOverConfiguredByteLimit() {
        var limited = new ProfilePictureValidator(100);
        MockMultipartFile oversized = new MockMultipartFile(
                "file", "avatar.png", "image/png", new byte[101]);
        assertThatThrownBy(() -> limited.validate(oversized))
                .isInstanceOf(ProfileFailure.class)
                .extracting(failure -> ((ProfileFailure) failure).code())
                .isEqualTo(ProfileFailure.Code.FILE_TOO_LARGE);
    }

    @Test
    void rejectsImagesWithDimensionsAboveMaximum() throws Exception {
        assertInvalid(image("png", "image/png", "large.png", 2049, 1));
    }

    private static void assertInvalid(MockMultipartFile file) {
        assertThatThrownBy(() -> validatorInstance().validate(file))
                .isInstanceOf(ProfileFailure.class)
                .extracting(failure -> ((ProfileFailure) failure).code())
                .isEqualTo(ProfileFailure.Code.INVALID_FILE);
    }

    private static ProfilePictureValidator validatorInstance() {
        return new ProfilePictureValidator(ProfilePictureValidator.DEFAULT_MAX_BYTES);
    }

    private static MockMultipartFile image(String format, String type, String filename, int width, int height)
            throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, format, bytes);
        return new MockMultipartFile("file", filename, type, bytes.toByteArray());
    }
}
