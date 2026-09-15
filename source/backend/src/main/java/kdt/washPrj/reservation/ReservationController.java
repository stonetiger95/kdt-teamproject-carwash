package kdt.washPrj.reservation;

import jakarta.validation.Valid;
import kdt.washPrj.global.pagenation.PageResponseDTO;
import kdt.washPrj.reservation.dto.AvailableSlotDTO;
import kdt.washPrj.reservation.dto.ReservationCancelDTO;
import kdt.washPrj.reservation.dto.ReservationCreateDTO;
import kdt.washPrj.reservation.dto.ReservationResponseDTO;
import kdt.washPrj.reservation.dto.ReservationSearchDTO;
import kdt.washPrj.reservation.dto.OwnerReservationDashboardDTO;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ReservationController {

    private final ReservationService reservationService;
    private final ReservationQueryService reservationQueryService;
    public ReservationController(
            ReservationService reservationService,
            ReservationQueryService reservationQueryService
    ) {
        this.reservationService = reservationService;
        this.reservationQueryService = reservationQueryService;
    }

    //가용 슬롯 조회
    @GetMapping("/customer/reservations/available-slots")
    public List<AvailableSlotDTO> getAvailableSlots(
            @RequestParam Long washSiteId,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) // 형식 확인 및 변환 "yyyy-mm-dd"
            LocalDate date
    ) {
        return reservationQueryService.getAvailableSlots(
                washSiteId,
                date
        );
    }

    // 예약 생성
    @PostMapping("/customer/reservations")
    public ResponseEntity<ReservationResponseDTO> createReservation(
            //ResponseEntity: https 상태값도 정하고, 본문body는 dto로 반환하겠다.
            Authentication authentication,
            @Valid @RequestBody ReservationCreateDTO dto
    ) {
        Long reservationId =
                reservationService.createReservation(
                        authentication.getName(),
                        dto
                );

        ReservationResponseDTO response =
                reservationQueryService.getCustomerReservation(
                        authentication.getName(),
                        reservationId
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
    // 고객 예약 목록 조회 및 페이지네이션 처리
    @GetMapping("/customer/reservations")
    public PageResponseDTO<ReservationResponseDTO>
    getCustomerReservationPage(
            Authentication authentication,
            @Valid ReservationSearchDTO search
    ) {
        return reservationQueryService.getCustomerReservationPage(
                authentication.getName(),
                search
        );
    }

    // 예약 상세보기
    @GetMapping("/customer/reservations/{reservationId}")
    public ReservationResponseDTO getCustomerReservation(
            Authentication authentication,
            @PathVariable Long reservationId
    ) {
        return reservationQueryService.getCustomerReservation(
                authentication.getName(),
                reservationId
        );
    }
    // 예약 취소
    @PatchMapping("/customer/reservations/{reservationId}/cancel")
    public ResponseEntity<Void> cancelCustomerReservation(
            Authentication authentication,
            @PathVariable Long reservationId,
            @Valid @RequestBody ReservationCancelDTO dto
    ) {
        reservationService.cancelCustomerReservation(
                authentication.getName(),
                reservationId,
                dto
        );

        return ResponseEntity.noContent().build();
    }
    // 점주 예약 목록 조회
    @GetMapping("/owner/reservations")
    public PageResponseDTO<ReservationResponseDTO>
    getOwnerReservationPage(
            Authentication authentication,
            @Valid ReservationSearchDTO search
    ) {
        return reservationQueryService.getOwnerReservationPage(
                authentication.getName(),
                search
        );
    }
    // 점주 - 예약 상세
    @GetMapping("/owner/reservations/{reservationId}")
    public ReservationResponseDTO getOwnerReservation(
            Authentication authentication,
            @PathVariable Long reservationId
    ) {
        return reservationQueryService.getOwnerReservation(
                authentication.getName(),
                reservationId
        );
    }
    // 점주 - 오늘 예약 대시보드 확인
    @GetMapping("/owner/reservations/dashboard")
    public OwnerReservationDashboardDTO getOwnerReservationDashboard(
            Authentication authentication,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date
    ) {
        return reservationQueryService.getOwnerReservationDashboard(
                authentication.getName(),
                date
        );
    }
    // 점주 예약 취소
    @PatchMapping("/owner/reservations/{reservationId}/cancel")
    public ResponseEntity<Void> cancelOwnerReservation(
            Authentication authentication,
            @PathVariable Long reservationId,
            @Valid @RequestBody ReservationCancelDTO dto
    ) {
        reservationService.cancelOwnerReservation(
                authentication.getName(),
                reservationId,
                dto
        );

        return ResponseEntity.noContent().build();
    }
    // 관리자 예약 목록 확인
    @GetMapping("/admin/reservations")
    public PageResponseDTO<ReservationResponseDTO>
    getAdminReservationPage(
            Authentication authentication,
            @Valid ReservationSearchDTO search
    ) {
        return reservationQueryService.getAdminReservationPage(
                authentication.getName(),
                search
        );
    }
    // 관리자 예약 상세 보기
    @GetMapping("/admin/reservations/{reservationId}")
    public ReservationResponseDTO getAdminReservation(
            Authentication authentication,
            @PathVariable Long reservationId
    ) {
        return reservationQueryService.getAdminReservation(
                authentication.getName(),
                reservationId
        );
    }
}
