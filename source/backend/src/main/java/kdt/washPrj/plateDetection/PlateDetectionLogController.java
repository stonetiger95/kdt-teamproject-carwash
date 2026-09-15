package kdt.washPrj.plateDetection;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import kdt.washPrj.global.pagenation.PageResponseDTO;
import kdt.washPrj.plateDetection.dto.PlateDetectionLogDetailDTO;
import kdt.washPrj.plateDetection.dto.PlateDetectionLogListDTO;
import kdt.washPrj.plateDetection.dto.PlateDetectionLogReviewDTO;
import kdt.washPrj.plateDetection.dto.PlateDetectionLogSearchDTO;
import kdt.washPrj.plateDetection.dto.PlateTrainingDataDTO;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/admin/plate-detection-logs")
public class PlateDetectionLogController {

    @Resource
    PlateDetectionService service;

    @GetMapping
    public PageResponseDTO<PlateDetectionLogListDTO> logList(
            Authentication authentication,
            @Valid PlateDetectionLogSearchDTO searchDTO
    ) {
        return service.detectionLogList(authentication.getName(), searchDTO);
    }

    @GetMapping("/{plateDetectionLogId}")
    public PlateDetectionLogDetailDTO logDetail(
            Authentication authentication,
            @PathVariable Integer plateDetectionLogId
    ) {
        return service.detectionLogDetail(authentication.getName(), plateDetectionLogId);
    }

    @GetMapping("/{plateDetectionLogId}/image")
    public ResponseEntity<org.springframework.core.io.Resource> logImage(
            Authentication authentication,
            @PathVariable Integer plateDetectionLogId
    ) {
        return service.detectionLogImage(authentication.getName(), plateDetectionLogId);
    }

    @PatchMapping("/{plateDetectionLogId}/review")
    public PlateDetectionLogDetailDTO reviewLog(
            Authentication authentication,
            @PathVariable Integer plateDetectionLogId,
            @Valid @RequestBody PlateDetectionLogReviewDTO request
    ) {
        return service.reviewDetectionLog(
                authentication.getName(),
                plateDetectionLogId,
                request
        );
    }

    @GetMapping("/training-data")
    public List<PlateTrainingDataDTO> trainingDataCandidates(
            Authentication authentication
    ) {
        return service.getTrainingDataCandidates(authentication.getName());
    }

    @GetMapping(value = "/training-data/export", produces = "application/zip")
    public ResponseEntity<StreamingResponseBody> downloadTrainingData(
            Authentication authentication
    ) {
        String timestamp = LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        );
        String filename = "plate-training-data-" + timestamp + ".zip";
        String loginId = authentication.getName();

        StreamingResponseBody stream = outputStream ->
                service.writeTrainingDataZip(loginId, outputStream);

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\""
                )
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(stream);
    }
}
