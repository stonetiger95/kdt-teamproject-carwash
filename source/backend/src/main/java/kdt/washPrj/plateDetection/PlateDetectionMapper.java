package kdt.washPrj.plateDetection;

import kdt.washPrj.plateDetection.dto.ExpiredPlateImageDTO;
import kdt.washPrj.plateDetection.dto.PlateBoundingBoxDTO;
import kdt.washPrj.plateDetection.dto.PlateDetectionLogDetailDTO;
import kdt.washPrj.plateDetection.dto.PlateDetectionLogListDTO;
import kdt.washPrj.plateDetection.dto.PlateDetectionLogReviewDTO;
import kdt.washPrj.plateDetection.dto.PlateDetectionLogSearchDTO;
import kdt.washPrj.plateDetection.dto.PlateTrainingDataDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PlateDetectionMapper {

    @Select("""
        SELECT app_api.create_plate_detection_log(
            #{detectedPlateNumber},
            #{yoloConfidence},
            #{ocrConfidence},
            #{errorLog},
            #{deviceId},
            #{filePath},
            #{fileExpireTime},
            #{detectedTime},
            #{yoloX1},
            #{yoloY1},
            #{yoloX2},
            #{yoloY2}
        )
    """)
    Integer insert(PlateDetectionLogVO vo);

    @Select("SELECT app_api.link_plate_detection_wash_history(#{plateDetectionLogId}, #{washHistoryId})")
    int updateWashHistoryId(
            @Param("plateDetectionLogId") Integer plateDetectionLogId,
            @Param("washHistoryId") Integer washHistoryId
    );

    @Select("""
        SELECT *
        FROM app_api.get_admin_plate_detection_log_list(
            #{loginId},
            #{search.failedOnly},
            #{search.needReviewOnly},
            #{search.startDate},
            #{search.endDate},
            #{search.plateNumber},
            #{search.size},
            #{search.offset}
        )
    """)
    List<PlateDetectionLogListDTO> getLogList(
            @Param("loginId") String loginId,
            @Param("search") PlateDetectionLogSearchDTO searchDTO
    );

    @Select("""
        SELECT app_api.count_admin_plate_detection_log_list(
            #{loginId},
            #{search.failedOnly},
            #{search.needReviewOnly},
            #{search.startDate},
            #{search.endDate},
            #{search.plateNumber}
        )
    """)
    int countLogList(
            @Param("loginId") String loginId,
            @Param("search") PlateDetectionLogSearchDTO searchDTO
    );

    @Select("SELECT * FROM app_api.get_admin_plate_detection_log_detail(#{loginId}, #{plateDetectionLogId})")
    PlateDetectionLogDetailDTO getLogDetail(
            @Param("loginId") String loginId,
            @Param("plateDetectionLogId") Integer plateDetectionLogId
    );

    @Select("""
        SELECT app_api.review_plate_detection_log(
            #{loginId},
            #{plateDetectionLogId},
            #{review.reviewStatus},
            #{review.correctedPlateNumber},
            #{review.failureReason},
            #{review.trainingUseYn},
            #{review.reviewMemo},
            #{review.bboxReviewStatus},
            #{box.x1},
            #{box.y1},
            #{box.x2},
            #{box.y2}
        )
    """)
    int upsertReview(
            @Param("loginId") String loginId,
            @Param("plateDetectionLogId") Integer plateDetectionLogId,
            @Param("review") PlateDetectionLogReviewDTO review,
            @Param("box") PlateBoundingBoxDTO box
    );

    @Select("SELECT app_api.get_admin_plate_log_file_path(#{loginId}, #{plateDetectionLogId})")
    String findFilePathById(
            @Param("loginId") String loginId,
            @Param("plateDetectionLogId") Integer plateDetectionLogId
    );

    @Select("SELECT * FROM app_api.get_expired_plate_images()")
    List<ExpiredPlateImageDTO> findExpiredImages();

    @Select("SELECT app_api.clear_expired_plate_image_path(#{plateDetectionLogId})")
    int clearExpiredImagePath(@Param("plateDetectionLogId") Integer plateDetectionLogId);

    @Select("SELECT app_api.active_device_exists(#{deviceId})")
    boolean existsActiveDevice(@Param("deviceId") Integer deviceId);

    @Select("SELECT * FROM app_api.get_admin_plate_training_data(#{loginId})")
    List<PlateTrainingDataDTO> getTrainingDataCandidates(@Param("loginId") String loginId);
}
