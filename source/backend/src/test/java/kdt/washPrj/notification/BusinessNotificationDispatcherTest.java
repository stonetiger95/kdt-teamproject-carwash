package kdt.washPrj.notification;

import kdt.washPrj.notification.dto.BusinessNotificationEventDTO;
import kdt.washPrj.notification.dto.BusinessNotificationTargetDTO;
import kdt.washPrj.predictiveMaintenance.TelegramNotificationClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BusinessNotificationDispatcherTest {

    private BusinessNotificationMapper mapper;
    private BusinessNotificationProperties properties;
    private LineNotificationClient lineClient;
    private TelegramNotificationClient telegramClient;
    private BusinessNotificationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        mapper = mock(BusinessNotificationMapper.class);
        properties = new BusinessNotificationProperties();
        properties.setEnabled(true);
        lineClient = mock(LineNotificationClient.class);
        telegramClient = mock(TelegramNotificationClient.class);
        dispatcher = new BusinessNotificationDispatcher(
                mapper,
                properties,
                lineClient,
                telegramClient
        );
    }

    @Test
    void reservationCreatedNotifiesLinkedCustomerAndOwnerThroughLine() {
        BusinessNotificationEventDTO event = event(
                1L,
                "RESERVATION_CREATED",
                10L
        );
        BusinessNotificationTargetDTO customer = lineTarget(
                "customer1",
                "CUSTOMER",
                "U11111111111111111111111111111111"
        );
        BusinessNotificationTargetDTO owner = lineTarget(
                "owner1",
                "OWNER",
                "U22222222222222222222222222222222"
        );
        when(mapper.selectTargets("RESERVATION_CREATED", 10L))
                .thenReturn(List.of(customer, owner));
        when(mapper.beginDelivery(
                eq(1L), eq("LINE"), eq(customer.getRecipient()),
                eq("customer1"), eq("CUSTOMER"), contains("[예약 완료]")
        )).thenReturn(101L);
        when(mapper.beginDelivery(
                eq(1L), eq("LINE"), eq(owner.getRecipient()),
                eq("owner1"), eq("OWNER"), contains("[새 예약]")
        )).thenReturn(102L);

        dispatcher.dispatchEvent(event);

        verify(lineClient).sendMessage(
                eq(customer.getRecipient()),
                contains("[예약 완료]")
        );
        verify(lineClient).sendMessage(
                eq(owner.getRecipient()),
                contains("[새 예약]")
        );
        verify(telegramClient, never()).sendMessage(org.mockito.ArgumentMatchers.anyString());
        verify(mapper).completeEvent(1L, "SENT", null, 0);
    }

    @Test
    void reservationCanceledNotifiesLinkedCustomerAndOwnerThroughLine() {
        BusinessNotificationEventDTO event = event(
                4L,
                "RESERVATION_CANCELED",
                25L
        );
        BusinessNotificationTargetDTO customer = lineTarget(
                "customer1",
                "CUSTOMER",
                "U11111111111111111111111111111111"
        );
        BusinessNotificationTargetDTO owner = lineTarget(
                "owner1",
                "OWNER",
                "U22222222222222222222222222222222"
        );
        when(mapper.selectTargets("RESERVATION_CANCELED", 25L))
                .thenReturn(List.of(customer, owner));
        when(mapper.beginDelivery(
                eq(4L), eq("LINE"), eq(customer.getRecipient()),
                eq("customer1"), eq("CUSTOMER"), contains("[예약 취소]")
        )).thenReturn(401L);
        when(mapper.beginDelivery(
                eq(4L), eq("LINE"), eq(owner.getRecipient()),
                eq("owner1"), eq("OWNER"), contains("[예약 취소]")
        )).thenReturn(402L);

        dispatcher.dispatchEvent(event);

        verify(lineClient).sendMessage(
                eq(customer.getRecipient()),
                contains("[예약 취소]")
        );
        verify(lineClient).sendMessage(
                eq(owner.getRecipient()),
                contains("[예약 취소]")
        );
        verify(mapper).completeEvent(4L, "SENT", null, 0);
    }

    @Test
    void ownerInquiryNotifiesAdminThroughTelegramOnly() {
        BusinessNotificationEventDTO event = event(
                2L,
                "INQUIRY_CREATED",
                20L
        );
        BusinessNotificationTargetDTO admin = new BusinessNotificationTargetDTO();
        admin.setChannel("TELEGRAM");
        admin.setTargetRole("ADMIN");
        admin.setActorRole("OWNER");
        admin.setTitle("장비 문의");

        when(mapper.selectTargets("INQUIRY_CREATED", 20L))
                .thenReturn(List.of(admin));
        when(telegramClient.getRecipient()).thenReturn("telegram-chat");
        when(mapper.beginDelivery(
                eq(2L), eq("TELEGRAM"), eq("telegram-chat"),
                eq(null), eq("ADMIN"), contains("[업주 문의 등록]")
        )).thenReturn(201L);

        dispatcher.dispatchEvent(event);

        verify(telegramClient).sendMessage(contains("장비 문의"));
        verifyNoInteractions(lineClient);
        verify(mapper).completeEvent(2L, "SENT", null, 0);
    }

    @Test
    void eventWithoutLinkedRecipientIsSkipped() {
        BusinessNotificationEventDTO event = event(
                3L,
                "PAYMENT_SUCCEEDED",
                30L
        );
        when(mapper.selectTargets("PAYMENT_SUCCEEDED", 30L))
                .thenReturn(List.of());

        dispatcher.dispatchEvent(event);

        verify(mapper).completeEvent(3L, "SKIPPED", null, 0);
        verifyNoInteractions(lineClient, telegramClient);
    }

    @Test
    void disabledDispatcherDoesNotClaimEvents() {
        properties.setEnabled(false);

        dispatcher.dispatchPendingEvents();

        verifyNoInteractions(mapper, lineClient, telegramClient);
    }

    private BusinessNotificationEventDTO event(
            Long eventId,
            String eventType,
            Long entityId
    ) {
        BusinessNotificationEventDTO event = new BusinessNotificationEventDTO();
        event.setEventId(eventId);
        event.setEventType(eventType);
        event.setEntityId(entityId);
        event.setAttempts(1);
        return event;
    }

    private BusinessNotificationTargetDTO lineTarget(
            String loginId,
            String role,
            String recipient
    ) {
        BusinessNotificationTargetDTO target = new BusinessNotificationTargetDTO();
        target.setChannel("LINE");
        target.setRecipient(recipient);
        target.setTargetLoginId(loginId);
        target.setTargetRole(role);
        target.setSiteName("테스트 세차장");
        target.setPlateNumber("12가3456");
        target.setReservationStartTime(LocalDateTime.of(2026, 8, 27, 10, 0));
        return target;
    }
}
