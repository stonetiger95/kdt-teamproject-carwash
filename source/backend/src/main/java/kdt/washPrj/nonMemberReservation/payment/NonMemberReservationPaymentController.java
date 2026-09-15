package kdt.washPrj.nonMemberReservation.payment;

import jakarta.validation.Valid;
import kdt.washPrj.nonMemberReservation.dto.NonMemberReservationCancelDTO;
import kdt.washPrj.nonMemberReservation.payment.dto.GuestReservationRefundResponseDTO;
import kdt.washPrj.nonMemberReservation.payment.dto.NonMemberReservationPaymentRequestDTO;
import kdt.washPrj.nonMemberReservation.payment.dto.NonMemberReservationPaymentResponseDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/public/non-member-reservations/payments")
public class NonMemberReservationPaymentController {

    private final NonMemberReservationPaymentService paymentService;
    private final String frontendBaseUrl;

    public NonMemberReservationPaymentController(
            NonMemberReservationPaymentService paymentService,
            @Value("${app.frontend-base-url}") String frontendBaseUrl
    ) {
        this.paymentService = paymentService;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @PostMapping
    public NonMemberReservationPaymentResponseDTO requestPayment(
            @Valid @RequestBody
            NonMemberReservationPaymentRequestDTO requestDTO
    ) {
        return paymentService.requestPayment(requestDTO);
    }

    @PostMapping("/{reservationId}/refund")
    public GuestReservationRefundResponseDTO refundPaidReservation(
            @PathVariable Long reservationId,
            @Valid @RequestBody NonMemberReservationCancelDTO requestDTO
    ) {
        return paymentService.refundPaidReservation(
                reservationId,
                requestDTO.getPhone(),
                requestDTO.getCancelReason()
        );
    }

    @PostMapping(
            value = "/payapp/feedback",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE
    )
    public String payAppFeedback(
            @RequestParam Map<String, String> params
    ) {
        return paymentService.handlePayAppFeedback(params);
    }

    @RequestMapping(
            value = "/payapp/return",
            method = {RequestMethod.GET, RequestMethod.POST}
    )
    public ResponseEntity<Void> returnPayment(
            @RequestParam Long reservationId,
            @RequestParam(required = false) Long paymentId
    ) {
        UriComponentsBuilder locationBuilder = UriComponentsBuilder
                .fromUriString(frontendBaseUrl)
                .path("/non-member-reservations/{reservationId}")
                .queryParamIfPresent(
                        "paymentId",
                        java.util.Optional.ofNullable(paymentId)
                );

        URI location = locationBuilder
                .buildAndExpand(reservationId)
                .encode()
                .toUri();

        return ResponseEntity
                .status(HttpStatus.SEE_OTHER)
                .location(location)
                .build();
    }
}
