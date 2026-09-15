package kdt.washPrj.testConsole;

import kdt.washPrj.prepaid.PrepaidMapper;
import kdt.washPrj.prepaid.PrepaidService;
import kdt.washPrj.testConsole.dto.TestConsoleActionResponseDTO;
import kdt.washPrj.testConsole.dto.TestConsoleAutoPayRequestDTO;
import kdt.washPrj.testConsole.dto.TestConsoleChargeRequestDTO;
import kdt.washPrj.testConsole.dto.TestConsoleOnsitePaymentRequestDTO;
import kdt.washPrj.testConsole.dto.TestConsoleStateDTO;
import kdt.washPrj.testConsole.dto.TestConsoleWashCompletionRequestDTO;
import kdt.washPrj.testConsole.dto.TestConsoleWashTargetDTO;
import kdt.washPrj.wash.WashService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestConsoleServiceTest {

    private TestConsoleMapper testConsoleMapper;
    private PrepaidMapper prepaidMapper;
    private PrepaidService prepaidService;
    private WashService washService;
    private TestConsoleService testConsoleService;

    @BeforeEach
    void setUp() throws Exception {
        testConsoleMapper = mock(TestConsoleMapper.class);
        prepaidMapper = mock(PrepaidMapper.class);
        prepaidService = mock(PrepaidService.class);
        washService = mock(WashService.class);
        testConsoleService = new TestConsoleService(
                testConsoleMapper,
                prepaidMapper,
                prepaidService,
                washService
        );
        setField("enabled", true);
    }

    @Test
    void getStateEnrichesSchemaAndBillingCardFlags() {
        TestConsoleStateDTO state = state(1L, 12000);
        when(testConsoleMapper.existsCustomer(1L)).thenReturn(true);
        when(testConsoleMapper.selectState(1L, 71L, 7000))
                .thenReturn(state);
        when(testConsoleMapper.isPrepaidSchemaReady()).thenReturn(true);
        when(testConsoleMapper.hasBillingCardTable()).thenReturn(true);
        when(testConsoleMapper.hasActiveBillingCard(1L)).thenReturn(true);
        when(testConsoleMapper.hasAutoPayBillingCard(1L)).thenReturn(true);

        TestConsoleStateDTO result =
                testConsoleService.getState(1L, 71L, 7000);

        assertTrue(result.getPrepaidSchemaReady());
        assertTrue(result.getBillingCardTableReady());
        assertTrue(result.getBillingCardRegistered());
        assertTrue(result.getAutoPayEnabled());
    }

    @Test
    void simulateSuccessfulChargeCreatesPaymentAndUpdatesWallet() {
        TestConsoleChargeRequestDTO request =
                new TestConsoleChargeRequestDTO();
        request.setCustomerId(1L);
        request.setAmount(5000);
        request.setResult("SUCCESS");
        when(testConsoleMapper.existsCustomer(1L)).thenReturn(true);
        when(testConsoleMapper.selectState(1L, null, 5000))
                .thenReturn(state(1L, 0));
        when(testConsoleMapper.selectState(1L, null, null))
                .thenReturn(state(1L, 5000));
        when(testConsoleMapper.isPrepaidSchemaReady()).thenReturn(true);
        when(testConsoleMapper.hasBillingCardTable()).thenReturn(false);
        when(prepaidMapper.reservePaymentHistoryId()).thenReturn(31L);

        TestConsoleActionResponseDTO result =
                testConsoleService.simulateCharge(request);

        assertEquals("TEST_CHARGE", result.getAction());
        assertEquals("SUCCESS", result.getResult());
        assertEquals(31L, result.getPaymentHistoryId());
        assertEquals(5000, result.getBalance());
        verify(prepaidMapper).insertSuccessfulTestChargePayment(
                31L,
                1L,
                5000,
                "TEST_CHARGE"
        );
        verify(prepaidService).completeChargePayment(31L, 5000);
    }

    @Test
    void retryAutoPaymentReturnsPaymentRequiredWithoutCardTable() {
        TestConsoleAutoPayRequestDTO request =
                new TestConsoleAutoPayRequestDTO();
        request.setCustomerId(1L);
        request.setWashHistoryId(71L);
        TestConsoleStateDTO state = state(1L, 1000);
        state.setEnoughBalance(false);
        state.setWashAmount(7000);
        when(testConsoleMapper.existsCustomer(1L)).thenReturn(true);
        when(testConsoleMapper.selectState(1L, 71L, null))
                .thenReturn(state);
        when(testConsoleMapper.isPrepaidSchemaReady()).thenReturn(true);
        when(testConsoleMapper.hasBillingCardTable()).thenReturn(false);

        TestConsoleActionResponseDTO result =
                testConsoleService.retryAutoPayment(request);

        assertEquals("PAYMENT_REQUIRED", result.getResult());
        assertFalse(result.getState().getBillingCardTableReady());
        verify(prepaidService, never()).autoUseWithBillingTopUp(1L, 71L);
    }

    @Test
    void simulateOnsitePaymentCreatesSuccessfulPayment() {
        TestConsoleOnsitePaymentRequestDTO request =
                new TestConsoleOnsitePaymentRequestDTO();
        request.setWashHistoryId(71L);
        request.setAmount(7000);
        request.setPaymentType("CARD");
        request.setResult("SUCCESS");
        TestConsoleWashTargetDTO target = new TestConsoleWashTargetDTO();
        target.setWashHistoryId(71L);
        target.setAmount(7000);
        target.setPaymentStatus("WAITING");
        when(testConsoleMapper.selectWashTargetForUpdate(71L))
                .thenReturn(target);
        when(testConsoleMapper.insertOnsitePayment(
                org.mockito.ArgumentMatchers.eq(71L),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(7000),
                org.mockito.ArgumentMatchers.eq("CARD"),
                org.mockito.ArgumentMatchers.eq("SUCCESS")
        )).thenReturn(41L);

        TestConsoleActionResponseDTO result =
                testConsoleService.simulateOnsitePayment(request);

        assertEquals("ONSITE_PAYMENT", result.getAction());
        assertEquals("SUCCESS", result.getResult());
        assertEquals(41L, result.getPaymentHistoryId());
        verify(testConsoleMapper).updateWashPaymentStatus(71L, "SUCCESS");
    }

    @Test
    void completeWashDelegatesToWashService() {
        TestConsoleWashCompletionRequestDTO request =
                new TestConsoleWashCompletionRequestDTO();
        request.setWashHistoryId(71L);
        TestConsoleWashTargetDTO target = new TestConsoleWashTargetDTO();
        target.setWashHistoryId(71L);
        target.setCustomerId(1L);
        target.setWashStatus("WASHING");
        when(testConsoleMapper.selectWashTargetForUpdate(71L))
                .thenReturn(target);
        when(washService.endWash(71L)).thenReturn(1);

        TestConsoleActionResponseDTO result =
                testConsoleService.completeWash(request);

        assertEquals("WASH_COMPLETE", result.getAction());
        assertEquals("SUCCESS", result.getResult());
        verify(washService).endWash(71L);
    }

    @Test
    void completeWalkInPaymentOnlyForNonMemberWalkIn() {
        TestConsoleWashCompletionRequestDTO request =
                new TestConsoleWashCompletionRequestDTO();
        request.setWashHistoryId(71L);
        TestConsoleWashTargetDTO target = new TestConsoleWashTargetDTO();
        target.setWashHistoryId(71L);
        target.setAmount(7000);
        target.setPaymentStatus("WAITING");
        when(testConsoleMapper.selectWashTargetForUpdate(71L))
                .thenReturn(target);
        when(testConsoleMapper.isNonMemberWalkIn(71L)).thenReturn(true);
        when(testConsoleMapper.insertOnsitePayment(
                71L, null, 7000, "CARD", "SUCCESS"
        )).thenReturn(42L);
        when(testConsoleMapper.updateWashPaymentStatus(71L, "SUCCESS"))
                .thenReturn(1);

        TestConsoleActionResponseDTO result =
                testConsoleService.completeWalkInPayment(request);

        assertEquals("WALK_IN_PAYMENT_COMPLETE", result.getAction());
        assertEquals("SUCCESS", result.getResult());
        assertEquals(42L, result.getPaymentHistoryId());
    }

    @Test
    void cleanupAllTestVehiclesClosesEveryOpenState() {
        when(testConsoleMapper.cleanupAllVehicles()).thenReturn(3);

        TestConsoleActionResponseDTO result =
                testConsoleService.cleanupAllTestVehicles();

        assertEquals("TEST_ALL_VEHICLES_CLEANUP", result.getAction());
        assertEquals("SUCCESS", result.getResult());
        assertEquals(3, result.getAmount());
        verify(testConsoleMapper).cleanupAllVehicles();
    }

    private TestConsoleStateDTO state(Long customerId, Integer balance) {
        TestConsoleStateDTO state = new TestConsoleStateDTO();
        state.setCustomerId(customerId);
        state.setBalance(balance);
        return state;
    }

    private void setField(String fieldName, Object value) throws Exception {
        Field field = TestConsoleService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(testConsoleService, value);
    }
}
