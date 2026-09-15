package kdt.washPrj.businessHour;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class BusinessHourService {

    private static final int DAYS_PER_WEEK = 7;

    private final BusinessHourMapper businessHourMapper;

    public BusinessHourService(BusinessHourMapper businessHourMapper) {
        this.businessHourMapper = businessHourMapper;
    }

    @Transactional(readOnly = true)
    public List<BusinessHourDTO> getOwnerBusinessHours(
            String loginId,
            Long washSiteId
    ) {
        requireOwnerWashSite(loginId, washSiteId);
        return businessHourMapper.selectBusinessHours(loginId, washSiteId);
    }

    @Transactional
    public void updateOwnerBusinessHours(
            String loginId,
            Long washSiteId,
            List<BusinessHourDTO> businessHours
    ) {
        requireOwnerWashSite(loginId, washSiteId);
        validateWeeklyBusinessHours(businessHours);

        for (BusinessHourDTO businessHour : businessHours) {
            normalizeBusinessHour(businessHour);

            int updatedCount = businessHourMapper.upsertBusinessHour(
                    loginId,
                    washSiteId,
                    businessHour
            );

            if (updatedCount != 1) {
                throw new ResponseStatusException(
                        INTERNAL_SERVER_ERROR,
                        "영업시간을 저장하지 못했습니다."
                );
            }
        }
    }

    @Transactional
    public void createDefaultBusinessHours(String loginId, Long washSiteId) {
        if (loginId == null || loginId.isBlank()) {
            throw new ResponseStatusException(
                    UNAUTHORIZED,
                    "로그인이 필요합니다."
            );
        }
        requireWashSiteId(washSiteId);

        int insertedCount =
                businessHourMapper.insertDefaultBusinessHours(loginId, washSiteId);

        if (insertedCount != DAYS_PER_WEEK) {
            throw new ResponseStatusException(
                    INTERNAL_SERVER_ERROR,
                    "기본 영업시간을 생성하지 못했습니다."
            );
        }
    }

    private void requireOwnerWashSite(
            String loginId,
            Long washSiteId
    ) {
        if (loginId == null || loginId.isBlank()) {
            throw new ResponseStatusException(
                    UNAUTHORIZED,
                    "로그인이 필요합니다."
            );
        }

        requireWashSiteId(washSiteId);

        if (!businessHourMapper.existsOwnerWashSite(
                loginId,
                washSiteId
        )) {
            throw new ResponseStatusException(
                    FORBIDDEN,
                    "해당 세차장의 영업시간을 관리할 수 없습니다."
            );
        }
    }

    private void requireWashSiteId(Long washSiteId) {
        if (washSiteId == null || washSiteId <= 0) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "세차장 ID를 확인해 주세요."
            );
        }
    }

    private void validateWeeklyBusinessHours(
            List<BusinessHourDTO> businessHours
    ) {
        if (businessHours == null
                || businessHours.size() != DAYS_PER_WEEK) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "월요일부터 일요일까지 7일의 영업시간이 필요합니다."
            );
        }

        Set<Integer> dayOfWeeks = new HashSet<>();

        for (BusinessHourDTO businessHour : businessHours) {
            validateBusinessHour(businessHour);

            if (!dayOfWeeks.add(businessHour.getDayOfWeek())) {
                throw new ResponseStatusException(
                        BAD_REQUEST,
                        "중복된 요일이 있습니다."
                );
            }
        }
    }

    private void validateBusinessHour(BusinessHourDTO businessHour) {
        if (businessHour == null
                || businessHour.getDayOfWeek() == null
                || businessHour.getDayOfWeek() < 1
                || businessHour.getDayOfWeek() > DAYS_PER_WEEK
                || businessHour.getClosed() == null
                || businessHour.getTwentyFourHours() == null) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "영업시간 입력값을 확인해 주세요."
            );
        }

        boolean closed = Boolean.TRUE.equals(businessHour.getClosed());
        boolean twentyFourHours =
                Boolean.TRUE.equals(businessHour.getTwentyFourHours());

        if (closed && twentyFourHours) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "휴무와 24시간 영업을 동시에 선택할 수 없습니다."
            );
        }

        if (closed || twentyFourHours) {
            return;
        }

        LocalTime openTime = businessHour.getOpenTime();
        LocalTime closeTime = businessHour.getCloseTime();

        if (openTime == null || closeTime == null) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "일반 영업일에는 시작 시간과 종료 시간이 필요합니다."
            );
        }

        if (!openTime.isBefore(closeTime)) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "영업 시작 시간은 종료 시간보다 빨라야 합니다."
            );
        }
    }

    private void normalizeBusinessHour(BusinessHourDTO businessHour) {
        if (Boolean.TRUE.equals(businessHour.getClosed())
                || Boolean.TRUE.equals(
                businessHour.getTwentyFourHours()
        )) {
            businessHour.setOpenTime(null);
            businessHour.setCloseTime(null);
        }
    }
}
