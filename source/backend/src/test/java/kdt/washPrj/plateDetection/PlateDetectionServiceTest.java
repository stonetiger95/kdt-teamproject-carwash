package kdt.washPrj.plateDetection;

import kdt.washPrj.global.file.FileStorageService;
import kdt.washPrj.global.pagenation.PageResponseDTO;
import kdt.washPrj.plateDetection.dto.ExpiredPlateImageDTO;
import kdt.washPrj.plateDetection.dto.PlateBoundingBoxDTO;
import kdt.washPrj.plateDetection.dto.PlateDetectionLogDetailDTO;
import kdt.washPrj.plateDetection.dto.PlateDetectionLogListDTO;
import kdt.washPrj.plateDetection.dto.PlateDetectionLogReviewDTO;
import kdt.washPrj.plateDetection.dto.PlateDetectionLogSearchDTO;
import kdt.washPrj.plateDetection.dto.PlateDetectionRequestDTO;
import kdt.washPrj.plateDetection.dto.PlateDetectionResponseDTO;
import kdt.washPrj.plateDetection.dto.PlateDetectionResultDTO;
import kdt.washPrj.plateDetection.dto.PlateTrainingDataDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PlateDetectionServiceTest {
    private static final String ADMIN_LOGIN_ID = "admin01";

    private FileStorageService fileStorageService;
    private PlateDetectionClient client;
    private PlateDetectionMapper mapper;
    private JsonMapper jsonMapper;
    private PlateDetectionService service;

    @BeforeEach
    void setUp() {
        fileStorageService = mock(FileStorageService.class);
        client = mock(PlateDetectionClient.class);
        mapper = mock(PlateDetectionMapper.class);
        jsonMapper = mock(JsonMapper.class);
        service = new PlateDetectionService();
        service.fileStorageService = fileStorageService;
        service.plateDetectionClient = client;
        service.mapper = mapper;
        service.jsonMapper = jsonMapper;
    }

    @Test
    void detectAndSavePersistsDetectionAndReturnsGeneratedId() {
        PlateDetectionRequestDTO request = request(1);
        PlateDetectionResultDTO detected = result(true, "AB123456");
        PlateBoundingBoxDTO box = new PlateBoundingBoxDTO();
        box.setX1(1);
        box.setY1(2);
        box.setX2(30);
        box.setY2(40);
        detected.setBbox(box);
        when(mapper.existsActiveDevice(1)).thenReturn(true);
        when(fileStorageService.save(request.getImage())).thenReturn("upload/plates/a.jpg");
        when(client.detection(request)).thenReturn(detected);
        when(mapper.insert(any())).thenReturn(99);

        PlateDetectionResponseDTO response = service.detectAndSave(request);

        assertEquals(99, response.getPlateDetectionLogId());
        assertEquals("AB123456", response.getDetectedPlateNumber());
        assertTrue(response.getSuccess());
        ArgumentCaptor<PlateDetectionLogVO> captor =
                ArgumentCaptor.forClass(PlateDetectionLogVO.class);
        verify(mapper).insert(captor.capture());
        assertEquals(1, captor.getValue().getYoloX1());
    }

    @Test
    void detectAndSaveRejectsInactiveDevice() {
        when(mapper.existsActiveDevice(2)).thenReturn(false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.detectAndSave(request(2))
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verifyNoInteractions(fileStorageService, client);
    }

    @Test
    void detectionLogListPassesAdminIdentityAndNormalizesSearch() {
        PlateDetectionLogSearchDTO search = new PlateDetectionLogSearchDTO();
        search.setPlateNumber(" AB123456 ");
        List<PlateDetectionLogListDTO> expected =
                List.of(new PlateDetectionLogListDTO());
        when(mapper.getLogList(ADMIN_LOGIN_ID, search)).thenReturn(expected);
        when(mapper.countLogList(ADMIN_LOGIN_ID, search)).thenReturn(1);

        PageResponseDTO<PlateDetectionLogListDTO> page =
                service.detectionLogList(ADMIN_LOGIN_ID, search);

        assertEquals("AB123456", search.getPlateNumber());
        assertSame(expected, page.getContent());
    }

    @Test
    void detectionLogListRejectsReversedDates() {
        PlateDetectionLogSearchDTO search = new PlateDetectionLogSearchDTO();
        search.setStartDate(LocalDate.of(2026, 7, 30));
        search.setEndDate(LocalDate.of(2026, 7, 1));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.detectionLogList(ADMIN_LOGIN_ID, search)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void detectionLogDetailUsesAdminIdentity() {
        PlateDetectionLogDetailDTO detail = new PlateDetectionLogDetailDTO();
        when(mapper.getLogDetail(ADMIN_LOGIN_ID, 1)).thenReturn(detail);

        assertSame(detail, service.detectionLogDetail(ADMIN_LOGIN_ID, 1));
    }

    @Test
    void detectionLogImageReturnsNotFoundWithoutStoredPath() {
        when(mapper.findFilePathById(ADMIN_LOGIN_ID, 1)).thenReturn(null);

        ResponseEntity<?> response =
                service.detectionLogImage(ADMIN_LOGIN_ID, 1);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void detectOnlyValidatesWithoutSaving() {
        PlateDetectionRequestDTO request = request(1);
        when(mapper.existsActiveDevice(1)).thenReturn(true);
        when(client.detection(request)).thenReturn(result(true, "AB123456"));

        PlateDetectionResponseDTO response = service.detectOnly(request);

        assertNull(response.getPlateDetectionLogId());
        assertEquals("AB123456", response.getDetectedPlateNumber());
        verify(fileStorageService).validate(request.getImage());
        verify(mapper, never()).insert(any());
    }

    @Test
    void deleteExpiredImagesClearsInvalidStoredPath() {
        ExpiredPlateImageDTO expired = new ExpiredPlateImageDTO();
        expired.setPlateDetectionLogId(3);
        expired.setFilePath("../outside.jpg");
        when(mapper.findExpiredImages()).thenReturn(List.of(expired));

        assertDoesNotThrow(service::deleteExpiredImages);

        verify(mapper).clearExpiredImagePath(3);
    }

    @Test
    void reviewDetectionLogUsesAdminIdentity() {
        PlateDetectionLogDetailDTO before = new PlateDetectionLogDetailDTO();
        PlateDetectionLogDetailDTO after = new PlateDetectionLogDetailDTO();
        when(mapper.getLogDetail(ADMIN_LOGIN_ID, 1)).thenReturn(before, after);
        when(mapper.upsertReview(
                eq(ADMIN_LOGIN_ID),
                eq(1),
                any(PlateDetectionLogReviewDTO.class),
                any(PlateBoundingBoxDTO.class)
        )).thenReturn(1);
        PlateDetectionLogReviewDTO review = new PlateDetectionLogReviewDTO();
        review.setBboxReviewStatus("NO_PLATE");
        review.setTrainingUseYn(false);

        assertSame(after, service.reviewDetectionLog(ADMIN_LOGIN_ID, 1, review));

        verify(mapper).upsertReview(
                eq(ADMIN_LOGIN_ID),
                eq(1),
                eq(review),
                any(PlateBoundingBoxDTO.class)
        );
    }

    @Test
    void exportTrainingDataCsvWritesBomHeader() {
        when(mapper.getTrainingDataCandidates(ADMIN_LOGIN_ID))
                .thenReturn(List.of(trainingRow()));

        String csv = new String(
                service.exportTrainingDataCsv(ADMIN_LOGIN_ID),
                StandardCharsets.UTF_8
        );

        assertTrue(csv.startsWith("\uFEFFplateDetectionLogId"));
    }

    @Test
    void exportTrainingDataJsonlWritesOneObjectPerLine() throws Exception {
        PlateTrainingDataDTO row = trainingRow();
        when(mapper.getTrainingDataCandidates(ADMIN_LOGIN_ID)).thenReturn(List.of(row));
        when(jsonMapper.writeValueAsString(row)).thenReturn("{\"id\":7}");

        String jsonl = new String(
                service.exportTrainingDataJsonl(ADMIN_LOGIN_ID),
                StandardCharsets.UTF_8
        );

        assertEquals("{\"id\":7}\n", jsonl);
    }

    @Test
    void writeTrainingDataZipIncludesMetadata() throws Exception {
        when(mapper.getTrainingDataCandidates(ADMIN_LOGIN_ID)).thenReturn(List.of());
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        service.writeTrainingDataZip(ADMIN_LOGIN_ID, output);

        boolean csv = false;
        boolean jsonl = false;
        try (ZipInputStream zip = new ZipInputStream(
                new ByteArrayInputStream(output.toByteArray()))) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
                csv |= entry.getName().equals("metadata/training-data.csv");
                jsonl |= entry.getName().equals("metadata/training-data.jsonl");
            }
        }
        assertTrue(csv && jsonl);
    }

    private PlateDetectionRequestDTO request(Integer deviceId) {
        PlateDetectionRequestDTO request = new PlateDetectionRequestDTO();
        request.setDeviceId(deviceId);
        request.setImage(mock(MultipartFile.class));
        return request;
    }

    private PlateDetectionResultDTO result(boolean success, String plate) {
        PlateDetectionResultDTO result = new PlateDetectionResultDTO();
        result.setSuccess(success);
        result.setDetectedPlateNumber(plate);
        result.setYoloConfidence(0.9);
        result.setOcrConfidence(0.8);
        result.setMessage(success ? "ok" : "failed");
        return result;
    }

    private PlateTrainingDataDTO trainingRow() {
        PlateTrainingDataDTO row = new PlateTrainingDataDTO();
        row.setPlateDetectionLogId(7);
        row.setDetectedPlateNumber("AB123456");
        return row;
    }
}
