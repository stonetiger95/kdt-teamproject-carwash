package kdt.washPrj.reservation;

import kdt.washPrj.global.pagenation.PageResponseDTO;
import kdt.washPrj.reservation.dto.AvailableSlotDTO;
import kdt.washPrj.reservation.dto.ReservationResponseDTO;
import kdt.washPrj.reservation.dto.ReservationSearchDTO;
import kdt.washPrj.reservation.dto.OwnerReservationDashboardDTO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ReservationQueryService {

    private final ReservationMapper reservationMapper;
    private final ReservationTimeProvider timeProvider;

    public ReservationQueryService(
            ReservationMapper reservationMapper,
            ReservationTimeProvider timeProvider
    ) {
        this.reservationMapper = reservationMapper;
        this.timeProvider = timeProvider;
    }

    public List<AvailableSlotDTO> getAvailableSlots(
            Long washSiteId,
            LocalDate date
    ) {
        validateAvailableSlotRequest(washSiteId, date);

        return reservationMapper.selectAvailableSlots(
                washSiteId,
                date
        );
    }

    public PageResponseDTO<ReservationResponseDTO>
    getCustomerReservationPage(
            String loginId,
            ReservationSearchDTO search
    ) {
        ReservationSearchDTO normalizedSearch =
                normalizeSearch(search);

        List<ReservationResponseDTO> content =
                reservationMapper.selectCustomerReservationList(
                        loginId,
                        normalizedSearch
                );

        int totalElements =
                reservationMapper.countCustomerReservationPage(
                        loginId,
                        normalizedSearch
                );

        return toPageResponse(
                content,
                normalizedSearch,
                totalElements
        );
    }

    public ReservationResponseDTO getCustomerReservation(
            String loginId,
            Long reservationId
    ) {
        ReservationResponseDTO reservation =
                reservationMapper.selectCustomerReservation(
                        loginId,
                        reservationId
                );

        return requireReservation(reservation);
    }

    public PageResponseDTO<ReservationResponseDTO>
    getOwnerReservationPage(
            String loginId,
            ReservationSearchDTO search
    ) {
        ReservationSearchDTO normalizedSearch =
                normalizeSearch(search);

        List<ReservationResponseDTO> content =
                reservationMapper.selectOwnerReservationPage(
                        loginId,
                        normalizedSearch
                );

        int totalElements =
                reservationMapper.countOwnerReservationPage(
                        loginId,
                        normalizedSearch
                );

        return toPageResponse(
                content,
                normalizedSearch,
                totalElements
        );
    }

    public ReservationResponseDTO getOwnerReservation(
            String loginId,
            Long reservationId
    ) {
        ReservationResponseDTO reservation =
                reservationMapper.selectOwnerReservation(
                        loginId,
                        reservationId
                );

        return requireReservation(reservation);
    }

    public OwnerReservationDashboardDTO getOwnerReservationDashboard(
            String loginId,
            LocalDate date
    ) {
        requireLoginId(loginId);
        LocalDate targetDate = date == null ? timeProvider.today() : date;

        OwnerReservationDashboardDTO dashboard =
                reservationMapper.selectOwnerReservationDashboard(
                        loginId,
                        targetDate
                );

        dashboard.setUpcomingReservations(
                reservationMapper.selectOwnerUpcomingReservations(
                        loginId,
                        targetDate
                )
        );
        return dashboard;
    }

    public PageResponseDTO<ReservationResponseDTO>
    getAdminReservationPage(
            String loginId,
            ReservationSearchDTO search
    ) {
        requireLoginId(loginId);
        ReservationSearchDTO normalizedSearch =
                normalizeSearch(search);

        List<ReservationResponseDTO> content =
                reservationMapper.selectAdminReservationPage(
                        loginId,
                        normalizedSearch
                );

        int totalElements =
                reservationMapper.countAdminReservationPage(
                        loginId,
                        normalizedSearch
                );

        return toPageResponse(
                content,
                normalizedSearch,
                totalElements
        );
    }

    public ReservationResponseDTO getAdminReservation(
            String loginId,
            Long reservationId
    ) {
        requireLoginId(loginId);
        ReservationResponseDTO reservation =
                reservationMapper.selectAdminReservation(
                        loginId,
                        reservationId
                );

        return requireReservation(reservation);
    }

    private ReservationSearchDTO normalizeSearch(
            ReservationSearchDTO search
    ) {
        ReservationSearchDTO normalized =
                search == null
                        ? new ReservationSearchDTO()
                        : search;

        if (normalized.getStartDate() != null
                && normalized.getEndDate() != null
                && normalized.getStartDate()
                .isAfter(normalized.getEndDate())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "종료일은 시작일보다 빠를 수 없습니다."
            );
        }

        String plateNumber = normalized.getPlateNumber();

        normalized.setPlateNumber(
                plateNumber == null || plateNumber.isBlank()
                        ? null
                        : plateNumber.trim()
        );

        return normalized;
    }

    private void requireLoginId(String loginId) {
        if (loginId == null || loginId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Login is required."
            );
        }
    }

    private void validateAvailableSlotRequest(
            Long washSiteId,
            LocalDate date
    ) {
        if (washSiteId == null || washSiteId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "세차장을 선택해 주세요."
            );
        }

        if (date == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "예약 날짜를 선택해 주세요."
            );
        }

        LocalDate today = timeProvider.today();

        if (date.isBefore(today)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "지난 날짜는 조회할 수 없습니다."
            );
        }

        if (date.isAfter(
                today.plusDays(ReservationPolicy.MAX_ADVANCE_DAYS)
        )) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "예약 가능 기간은 7일 이내입니다."
            );
        }
    }

    private ReservationResponseDTO requireReservation(
            ReservationResponseDTO reservation
    ) {
        if (reservation == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "예약을 찾을 수 없습니다."
            );
        }

        return reservation;
    }

    private PageResponseDTO<ReservationResponseDTO>
    toPageResponse(
            List<ReservationResponseDTO> content,
            ReservationSearchDTO search,
            int totalElements
    ) {
        return new PageResponseDTO<>(
                content,
                search.getPage(),
                search.getSize(),
                totalElements
        );
    }
}
