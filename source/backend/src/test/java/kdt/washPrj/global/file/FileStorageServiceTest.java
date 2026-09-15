package kdt.washPrj.global.file;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileStorageServiceTest {

    private FileStorageService service;
    private MockMultipartFile image;

    @BeforeEach
    void setUp() throws Exception {
        service = new FileStorageService();

        BufferedImage bufferedImage =
                new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(bufferedImage, "png", output);
        bufferedImage.flush();

        image = new MockMultipartFile(
                "image",
                "plate.png",
                "image/png",
                output.toByteArray()
        );
    }

    @Test
    void validateAcceptsDecodedPngImage() {
        assertDoesNotThrow(() -> service.validate(image));
    }

    @Test
    void saveWritesValidatedImageWithSafeGeneratedName()
            throws Exception {
        String savedPath = service.save(image);
        Path path = Path.of(savedPath);

        try {
            assertFalse(savedPath.contains("\\"));
            assertTrue(path.startsWith(Path.of("upload", "plates")));
            assertTrue(savedPath.endsWith(".png"));
            assertTrue(Files.exists(path));
        } finally {
            Files.deleteIfExists(path);
        }
    }
}
