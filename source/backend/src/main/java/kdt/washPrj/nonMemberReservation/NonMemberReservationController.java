package kdt.washPrj.nonMemberReservation;

import jakarta.validation.Valid;
import kdt.washPrj.nonMemberReservation.dto.NonMemberReservationAccessDTO;
import kdt.washPrj.nonMemberReservation.dto.NonMemberReservationCancelDTO;
import kdt.washPrj.nonMemberReservation.dto.NonMemberReservationCreateDTO;
import kdt.washPrj.nonMemberReservation.dto.NonMemberReservationResponseDTO;
import kdt.washPrj.nonMemberReservation.payment.NonMemberReservationPaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/non-member-reservations")
public class NonMemberReservationController {

    private final NonMemberReservationService service;
    private final NonMemberReservationPaymentService paymentService;

    public NonMemberReservationController(
            NonMemberReservationService service,
            NonMemberReservationPaymentService paymentService
    ) {
        this.service = service;
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<NonMemberReservationResponseDTO>
    createPendingReservation(
            @Valid @RequestBody NonMemberReservationCreateDTO dto
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(service.createPendingReservation(dto));
    }

    @PostMapping("/lookup")
    public NonMemberReservationResponseDTO getReservation(
            @Valid @RequestBody NonMemberReservationAccessDTO dto
    ) {
        return service.getReservation(dto);
    }

    @PatchMapping("/{reservationId}/cancel")
    public ResponseEntity<?> cancelReservation(
            @PathVariable Long reservationId,
            @Valid @RequestBody NonMemberReservationCancelDTO dto
    ) {
        NonMemberReservationAccessDTO accessDTO =
                new NonMemberReservationAccessDTO();
        accessDTO.setReservationId(reservationId);
        accessDTO.setPhone(dto.getPhone());
        NonMemberReservationResponseDTO reservation =
                service.getReservation(accessDTO);

        if (reservation.getStatus() != null
                && "CONFIRMED".equals(reservation.getStatus().name())
                && "PAID".equals(reservation.getPaymentStatus())) {
            return ResponseEntity.ok(
                    paymentService.refundPaidReservation(
                            reservationId,
                            dto.getPhone(),
                            dto.getCancelReason()
                    )
            );
        }

        service.cancelReservation(reservationId, dto);
        return ResponseEntity.noContent().build();
    }
}
