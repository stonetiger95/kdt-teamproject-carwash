package kdt.washPrj.plateDetection;

import jakarta.annotation.Resource;
import kdt.washPrj.global.file.FileStorageService;
import kdt.washPrj.global.pagenation.PageResponseDTO;
import kdt.washPrj.global.vehicle.PlateNumberNormalizer;
import kdt.washPrj.plateDetection.dto.*;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.core.io.UrlResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;


@Service
@RequiredArgsConstructor
public class PlateDetectionService {
    private static final Logger log = LoggerFactory.getLogger(PlateDetectionService.class);
    private static final Path PLATE_UPLOAD_ROOT = Paths.get("upload/plates")
            .toAbsolutePath()
            .normalize();

    @Resource
    FileStorageService fileStorageService;
    @Resource
    PlateDetectionClient plateDetectionClient;
    @Resource
    PlateDetectionMapper mapper;
    @Resource
    JsonMapper jsonMapper;


    @Transactional
    public PlateDetectionResponseDTO detectAndSave(PlateDetectionRequestDTO request){
        //0. 장비 상태 확인
        // 해당 장비 아이디가 있는지
        // 활성화 및 삭제 여부를 확인
        validateActiveDevice(request.getDeviceId());

        //1. 이미지 저장
        String filePath = fileStorageService.save(request.getImage());

        //2.YOLO + OCR 호출
        PlateDetectionResultDTO result =
                plateDetectionClient.detection(request);

        //3.탐지로그 저장
        PlateDetectionLogVO log = new PlateDetectionLogVO();
        log.setDeviceId(request.getDeviceId());
        log.setDetectedPlateNumber(
                PlateNumberNormalizer.normalize(result.getDetectedPlateNumber())
        );
        log.setOcrConfidence(result.getOcrConfidence());
        log.setYoloConfidence(result.getYoloConfidence());
        if (result.getBbox() != null) {
            log.setYoloX1(result.getBbox().getX1());
            log.setYoloY1(result.getBbox().getY1());
            log.setYoloX2(result.getBbox().getX2());
            log.setYoloY2(result.getBbox().getY2());
        }
        log.setFilePath(filePath);
        log.setDetectedTime(LocalDateTime.now());
        log.setFileExpireTime(LocalDateTime.now().plusDays(7));

        // 인식 실패면 error_log에 실패 사유 저장
        if (!Boolean.TRUE.equals(result.getSuccess())) {
            log.setErrorLog(result.getMessage());
        } else {
            log.setErrorLog(null);
        }

        Integer logId = mapper.insert(log);
        log.setPlateDetectionLogId(logId);

        //4. 응답 반환
        PlateDetectionResponseDTO response = new PlateDetectionResponseDTO();
        response.setPlateDetectionLogId(log.getPlateDetectionLogId());
        response.setDetectedPlateNumber(log.getDetectedPlateNumber());
        response.setYoloConfidence(log.getYoloConfidence());
        response.setOcrConfidence(log.getOcrConfidence());
        response.setSuccess(Boolean.TRUE.equals(result.getSuccess()));
        response.setMessage(result.getMessage());

        return response;
    }

    //번호판 탐지 기록 <목록>
    public PageResponseDTO<PlateDetectionLogListDTO> detectionLogList(
            String loginId,
            PlateDetectionLogSearchDTO searchDTO
    ){
        if (searchDTO.getStartDate() != null
                && searchDTO.getEndDate() != null
                && searchDTO.getStartDate().isAfter(searchDTO.getEndDate())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "시작일은 종료일보다 늦을 수 없습니다."
            );
        }

        if (searchDTO.getPlateNumber() != null) {
            searchDTO.setPlateNumber(searchDTO.getPlateNumber().trim());
        }

        List<PlateDetectionLogListDTO> list = mapper.getLogList(loginId, searchDTO);
        int totalCount = mapper.countLogList(loginId, searchDTO);

        return new PageResponseDTO<>(
                list,
                searchDTO.getPage(),
                searchDTO.getSize(),
                totalCount
        );
    }
    // 번호판 탐지 기록 <상세>
    public PlateDetectionLogDetailDTO detectionLogDetail(
            String loginId,
            Integer plateDetectionLogId
    ) {
        PlateDetectionLogDetailDTO detail =
                mapper.getLogDetail(loginId, plateDetectionLogId);
        if (detail == null) {
            throw new RuntimeException("번호판 탐지 로그를 찾을 수 없습니다.");
        }

        return detail;
    }
    // 번호판 탐지 이미지
    public ResponseEntity<org.springframework.core.io.Resource> detectionLogImage(
            String loginId,
            Integer plateDetectionLogId
    ) {
        try {
            String filePath = mapper.findFilePathById(loginId, plateDetectionLogId);

            if (filePath == null || filePath.isBlank()) {
                return ResponseEntity.notFound().build();
            }

            Path path = Paths.get(filePath).toAbsolutePath().normalize();

            if (!path.startsWith(PLATE_UPLOAD_ROOT)
                    || !Files.isRegularFile(path)) {
                return ResponseEntity.notFound().build();
            }

            org.springframework.core.io.Resource resource =
                    new UrlResource(path.toUri());

            String detectedContentType = Files.probeContentType(path);
            MediaType contentType = detectedContentType == null
                    ? MediaType.APPLICATION_OCTET_STREAM
                    : MediaType.parseMediaType(detectedContentType);

            if (!MediaType.IMAGE_JPEG.isCompatibleWith(contentType)
                    && !MediaType.IMAGE_PNG.isCompatibleWith(contentType)) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .contentType(contentType)
                    .body(resource);

        } catch (Exception e) {
            throw new RuntimeException("번호판 탐지 이미지를 불러오지 못했습니다.", e);
        }
    }
    public PlateDetectionResponseDTO detectOnly(
            PlateDetectionRequestDTO request
    ) {
        validateActiveDevice(request.getDeviceId());
        fileStorageService.validate(request.getImage());

        PlateDetectionResultDTO result =
                plateDetectionClient.detection(request);

        PlateDetectionResponseDTO response =
                new PlateDetectionResponseDTO();

        response.setPlateDetectionLogId(null);
        response.setDetectedPlateNumber(
                result.getDetectedPlateNumber()
        );
        response.setYoloConfidence(
                result.getYoloConfidence()
        );
        response.setOcrConfidence(
                result.getOcrConfidence()
        );
        response.setSuccess(
                Boolean.TRUE.equals(result.getSuccess())
        );
        response.setMessage(result.getMessage());

        return response;
    }

    @Scheduled(fixedDelay = 3_600_000L, initialDelay = 60_000L)
    @Transactional
    public void deleteExpiredImages() {
        List<ExpiredPlateImageDTO> expiredImages = mapper.findExpiredImages();

        for (ExpiredPlateImageDTO expiredImage : expiredImages) {
            deleteStoredFile(expiredImage.getFilePath());
            mapper.clearExpiredImagePath(expiredImage.getPlateDetectionLogId());
        }

        if (!expiredImages.isEmpty()) {
            log.info("Deleted {} expired plate images.", expiredImages.size());
        }
    }

    private void deleteStoredFile(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }

        try {
            Path imagePath = Paths.get(filePath).toAbsolutePath().normalize();

            if (!imagePath.startsWith(PLATE_UPLOAD_ROOT)) {
                log.warn("Skipped expired image outside upload directory: {}", filePath);
                return;
            }

            Files.deleteIfExists(imagePath);
        } catch (Exception e) {
            log.warn("Failed to delete expired plate image: {}", filePath, e);
        }
    }

    // 번호판 리뷰 저장 후 다시 상세 반환
    @Transactional
    public PlateDetectionLogDetailDTO reviewDetectionLog(
            String loginId,
            Integer plateDetectionLogId,
            PlateDetectionLogReviewDTO reviewDTO
    ) {
        PlateDetectionLogDetailDTO detail =
                detectionLogDetail(loginId, plateDetectionLogId);

        PlateBoundingBoxDTO reviewedBox =
                resolveReviewedBox(detail, reviewDTO);

        int reviewResult = mapper.upsertReview(
                loginId,
                plateDetectionLogId,
                reviewDTO,
                reviewedBox
        );

        if (reviewResult == -1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "원본 이미지가 없어 학습 데이터로 보관할 수 없습니다."
            );
        }

        if (reviewResult == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "번호판 탐지 로그를 찾을 수 없습니다."
            );
        }

        return detectionLogDetail(loginId, plateDetectionLogId);
    }

    private PlateBoundingBoxDTO resolveReviewedBox(
            PlateDetectionLogDetailDTO detail,
            PlateDetectionLogReviewDTO review
    ) {
        String status = review.getBboxReviewStatus();

        if ("NO_PLATE".equals(status)) {
            return new PlateBoundingBoxDTO();
        }

        if ("ACCEPTED".equals(status)) {
            if (detail.getYoloX1() == null
                    || detail.getYoloY1() == null
                    || detail.getYoloX2() == null
                    || detail.getYoloY2() == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "승인할 YOLO bbox가 없습니다."
                );
            }

            PlateBoundingBoxDTO box = new PlateBoundingBoxDTO();
            box.setX1(detail.getYoloX1());
            box.setY1(detail.getYoloY1());
            box.setX2(detail.getYoloX2());
            box.setY2(detail.getYoloY2());
            return box;
        }

        if ("CORRECTED".equals(status)) {
            PlateBoundingBoxDTO box = review.getReviewedBox();

            if (!isValidBox(box)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "보정한 bbox 좌표가 올바르지 않습니다."
                );
            }

            return box;
        }

        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "bbox 검수 상태가 필요합니다."
        );
    }

    public List<PlateTrainingDataDTO> getTrainingDataCandidates(String loginId) {
        return mapper.getTrainingDataCandidates(loginId);
    }

    public byte[] exportTrainingDataCsv(String loginId) {
        List<PlateTrainingDataDTO> candidates =
                mapper.getTrainingDataCandidates(loginId);

        // Excel에서 한글이 깨지지 않도록 UTF-8 BOM 추가
        StringBuilder csv = new StringBuilder("\uFEFF");

        csv.append(
                "plateDetectionLogId,"
                        + "filePath,"
                        + "detectedTime,"
                        + "detectedPlateNumber,"
                        + "yoloConfidence,"
                        + "ocrConfidence,"
                        + "yoloX1,"
                        + "yoloY1,"
                        + "yoloX2,"
                        + "yoloY2,"
                        + "correctedPlateNumber,"
                        + "finalPlateNumber,"
                        + "reviewStatus,"
                        + "bboxReviewStatus,"
                        + "reviewedX1,"
                        + "reviewedY1,"
                        + "reviewedX2,"
                        + "reviewedY2,"
                        + "reviewedAt,"
                        + "reviewerId"
        );
        csv.append("\r\n");

        for (PlateTrainingDataDTO candidate : candidates) {
            Object[] values = {
                    candidate.getPlateDetectionLogId(),
                    candidate.getFilePath(),
                    candidate.getDetectedTime(),
                    candidate.getDetectedPlateNumber(),
                    candidate.getYoloConfidence(),
                    candidate.getOcrConfidence(),
                    candidate.getYoloX1(),
                    candidate.getYoloY1(),
                    candidate.getYoloX2(),
                    candidate.getYoloY2(),
                    candidate.getCorrectedPlateNumber(),
                    candidate.getFinalPlateNumber(),
                    candidate.getReviewStatus(),
                    candidate.getBboxReviewStatus(),
                    candidate.getReviewedX1(),
                    candidate.getReviewedY1(),
                    candidate.getReviewedX2(),
                    candidate.getReviewedY2(),
                    candidate.getReviewedAt(),
                    candidate.getReviewerId()
            };

            for (int i = 0; i < values.length; i++) {
                if (i > 0) {
                    csv.append(',');
                }

                csv.append(toCsvValue(values[i]));
            }

            csv.append("\r\n");
        }

        return csv.toString()
                .getBytes(StandardCharsets.UTF_8);
    }
    public byte[] exportTrainingDataJsonl(String loginId) {
        List<PlateTrainingDataDTO> candidates =
                mapper.getTrainingDataCandidates(loginId);

        StringBuilder jsonl = new StringBuilder();

        try {
            for (PlateTrainingDataDTO candidate : candidates) {
                // DTO 하나를 JSON 한 줄로 변환
                jsonl.append(
                        jsonMapper.writeValueAsString(candidate)
                );
                jsonl.append('\n');
            }
        } catch (JacksonException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "학습 데이터 JSONL 생성에 실패했습니다.",
                    e
            );
        }

        return jsonl.toString()
                .getBytes(StandardCharsets.UTF_8);
    }

    public void writeTrainingDataZip(
            String loginId,
            OutputStream outputStream
    ) throws IOException {
        List<PlateTrainingDataDTO> candidates =
                mapper.getTrainingDataCandidates(loginId);

        ZipOutputStream zipOutputStream =
                new ZipOutputStream(
                        new BufferedOutputStream(outputStream)
                );

        try {
            // 기존 JSONL/CSV도 ZIP 내부에 포함
            addZipEntry(
                    zipOutputStream,
                    "metadata/training-data.jsonl",
                    exportTrainingDataJsonl(loginId)
            );

            addZipEntry(
                    zipOutputStream,
                    "metadata/training-data.csv",
                    exportTrainingDataCsv(loginId)
            );

            StringBuilder missingImages =
                    new StringBuilder();

            for (PlateTrainingDataDTO candidate : candidates) {
                try {
                    Path imagePath = resolveTrainingImagePath(
                            candidate.getFilePath()
                    );

                    if (imagePath == null) {
                        appendMissingImage(missingImages, candidate);
                        continue;
                    }

                    String extension =
                            getTrainingImageExtension(imagePath);

                    if (extension == null) {
                        appendMissingImage(missingImages, candidate);
                        continue;
                    }

                    byte[] imageBytes = Files.readAllBytes(imagePath);

                    String zipImagePath =
                            "images/"
                                    + candidate.getPlateDetectionLogId()
                                    + extension;

                    addZipEntry(
                            zipOutputStream,
                            zipImagePath,
                            imageBytes
                    );
                } catch (IOException | RuntimeException e) {
                    log.warn(
                            "Skipped plate training image. plateDetectionLogId={}, filePath={}",
                            candidate.getPlateDetectionLogId(),
                            candidate.getFilePath(),
                            e
                    );
                    appendMissingImage(missingImages, candidate);
                }
            }

            if (!missingImages.isEmpty()) {
                addZipEntry(
                        zipOutputStream,
                        "metadata/missing-images.txt",
                        missingImages.toString()
                                .getBytes(StandardCharsets.UTF_8)
                );
            }

            String readme = """
                번호판 학습 후보 데이터

                images/
                - 파일명은 plateDetectionLogId입니다.

                metadata/training-data.jsonl
                - 학습 후보 검수 및 bbox 정보입니다.

                metadata/training-data.csv
                - 동일한 데이터를 CSV 형식으로 제공합니다.

                bbox 사용 우선순위
                1. reviewed 좌표
                2. YOLO 좌표
                3. NO_PLATE는 음성 학습 후보
                """;

            addZipEntry(
                    zipOutputStream,
                    "README.txt",
                    readme.getBytes(StandardCharsets.UTF_8)
            );

        } finally {
            // HTTP 응답 OutputStream은 닫지 않고 ZIP 데이터만 마무리한다.
            zipOutputStream.finish();
            zipOutputStream.flush();
        }
    }

    private void addZipEntry(
            ZipOutputStream zipOutputStream,
            String entryName,
            byte[] content
    ) throws IOException {
        zipOutputStream.putNextEntry(
                new ZipEntry(entryName)
        );

        zipOutputStream.write(content);
        zipOutputStream.closeEntry();
    }

    private void appendMissingImage(
            StringBuilder missingImages,
            PlateTrainingDataDTO candidate
    ) {
        missingImages
                .append(candidate.getPlateDetectionLogId())
                .append('\t')
                .append(candidate.getFilePath())
                .append('\n');
    }

    private Path resolveTrainingImagePath(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return null;
        }

        Path imagePath;

        try {
            imagePath = Paths.get(filePath)
                    .toAbsolutePath()
                    .normalize();
        } catch (InvalidPathException e) {
            return null;
        }

        if (!imagePath.startsWith(PLATE_UPLOAD_ROOT)) {
            return null;
        }

        if (!Files.isRegularFile(imagePath)) {
            return null;
        }

        return imagePath;
    }

    private String getTrainingImageExtension(Path imagePath) {
        String filename = imagePath
                .getFileName()
                .toString()
                .toLowerCase(Locale.ROOT);

        if (filename.endsWith(".jpeg")) {
            return ".jpeg";
        }

        if (filename.endsWith(".jpg")) {
            return ".jpg";
        }

        if (filename.endsWith(".png")) {
            return ".png";
        }

        return null;
    }

    private String toCsvValue(Object value) {
        if (value == null) {
            return "";
        }

        String text = String.valueOf(value);

        // 값 안의 큰따옴표는 CSV 규칙에 따라 두 번 작성
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private boolean isValidBox(PlateBoundingBoxDTO box) {
        return box != null
                && box.getX1() != null
                && box.getY1() != null
                && box.getX2() != null
                && box.getY2() != null
                && box.getX1() >= 0
                && box.getY1() >= 0
                && box.getX2() > box.getX1()
                && box.getY2() > box.getY1();
    }

    private void validateActiveDevice(Integer deviceId) {
        if (deviceId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "장비 ID가 필요합니다."
            );
        }

        if (!mapper.existsActiveDevice(deviceId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "비활성화되었거나 존재하지 않는 장비입니다."
            );
        }
    }



}
