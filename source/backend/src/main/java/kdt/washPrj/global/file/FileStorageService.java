package kdt.washPrj.global.file;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final int MAX_IMAGE_DIMENSION = 5_000;
    private static final long MAX_IMAGE_PIXELS = 12_000_000L;
    private static final Set<String> GENERIC_CONTENT_TYPES = Set.of(
            "",
            "application/octet-stream"
    );

    private final Path relativeUploadDir = Paths.get("upload/plates");
    private final Path uploadDir = relativeUploadDir.toAbsolutePath().normalize();

    public void validate(MultipartFile file) {
        byte[] content = readFile(file);
        AllowedImageType imageType = detectImageType(content);

        validateDeclaredContentType(
                file.getContentType(),
                imageType
        );
        validateImage(content, imageType);
    }

    public String save(MultipartFile file) {
        byte[] content = readFile(file);
        AllowedImageType imageType = detectImageType(content);

        validateDeclaredContentType(file.getContentType(), imageType);
        validateImage(content, imageType);

        String savedName =
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMdd_HHmmss"))
                        + "_" + UUID.randomUUID()
                        + imageType.extension;

        Path targetPath = uploadDir.resolve(savedName).normalize();

        if (!targetPath.startsWith(uploadDir)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "올바르지 않은 이미지 저장 경로입니다."
            );
        }

        try {
            Files.createDirectories(uploadDir);
            Files.write(targetPath, content, StandardOpenOption.CREATE_NEW);
            return relativeUploadDir
                    .resolve(savedName)
                    .toString()
                    .replace('\\', '/');
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "이미지 저장에 실패했습니다.",
                    e
            );
        }
    }

    private byte[] readFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "이미지 파일이 필요합니다."
            );
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.CONTENT_TOO_LARGE,
                    "이미지 파일은 10MB를 초과할 수 없습니다."
            );
        }

        try {
            byte[] content = file.getBytes();

            if (content.length == 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "빈 이미지 파일은 업로드할 수 없습니다."
                );
            }

            if (content.length > MAX_FILE_SIZE) {
                throw new ResponseStatusException(
                        HttpStatus.CONTENT_TOO_LARGE,
                        "이미지 파일은 10MB를 초과할 수 없습니다."
                );
            }

            return content;
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "이미지 파일을 읽을 수 없습니다.",
                    e
            );
        }
    }

    private AllowedImageType detectImageType(byte[] content) {
        if (isJpeg(content)) {
            return AllowedImageType.JPEG;
        }

        if (isPng(content)) {
            return AllowedImageType.PNG;
        }

        throw new ResponseStatusException(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "JPEG 또는 PNG 이미지 파일만 업로드할 수 있습니다."
        );
    }

    private void validateDeclaredContentType(
            String declaredContentType,
            AllowedImageType imageType
    ) {
        String normalizedContentType = declaredContentType == null
                ? ""
                : declaredContentType.toLowerCase(Locale.ROOT).trim();

        if (!GENERIC_CONTENT_TYPES.contains(normalizedContentType)
                && !imageType.contentTypes.contains(normalizedContentType)) {
            throw new ResponseStatusException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "요청의 파일 형식과 실제 이미지 형식이 일치하지 않습니다."
            );
        }
    }

    private void validateImage(
            byte[] content,
            AllowedImageType expectedType
    ) {
        try (ImageInputStream imageInput = ImageIO.createImageInputStream(
                new ByteArrayInputStream(content)
        )) {
            if (imageInput == null) {
                throw invalidImage();
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);

            if (!readers.hasNext()) {
                throw invalidImage();
            }

            ImageReader reader = readers.next();

            try {
                reader.setInput(imageInput, true, true);

                String formatName = reader.getFormatName();

                if (!expectedType.formatNames.contains(formatName.toUpperCase(Locale.ROOT))) {
                    throw invalidImage();
                }

                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                long pixels = (long) width * height;

                if (width <= 0
                        || height <= 0
                        || width > MAX_IMAGE_DIMENSION
                        || height > MAX_IMAGE_DIMENSION
                        || pixels > MAX_IMAGE_PIXELS) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "이미지 해상도가 허용 범위를 초과했습니다."
                    );
                }

                BufferedImage decodedImage = reader.read(0);

                if (decodedImage == null) {
                    throw invalidImage();
                }

                decodedImage.flush();
            } finally {
                reader.dispose();
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "손상되었거나 지원하지 않는 이미지 파일입니다.",
                    e
            );
        }
    }

    private ResponseStatusException invalidImage() {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "손상되었거나 지원하지 않는 이미지 파일입니다."
        );
    }

    private boolean isJpeg(byte[] content) {
        return content.length >= 3
                && (content[0] & 0xff) == 0xff
                && (content[1] & 0xff) == 0xd8
                && (content[2] & 0xff) == 0xff;
    }

    private boolean isPng(byte[] content) {
        int[] signature = {0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};

        if (content.length < signature.length) {
            return false;
        }

        for (int i = 0; i < signature.length; i++) {
            if ((content[i] & 0xff) != signature[i]) {
                return false;
            }
        }

        return true;
    }

    private enum AllowedImageType {
        JPEG(
                ".jpg",
                Set.of("image/jpeg", "image/jpg", "image/pjpeg"),
                Set.of("JPEG", "JPG")
        ),
        PNG(
                ".png",
                Set.of("image/png"),
                Set.of("PNG")
        );

        private final String extension;
        private final Set<String> contentTypes;
        private final Set<String> formatNames;

        AllowedImageType(
                String extension,
                Set<String> contentTypes,
                Set<String> formatNames
        ) {
            this.extension = extension;
            this.contentTypes = contentTypes;
            this.formatNames = formatNames;
        }
    }
}
