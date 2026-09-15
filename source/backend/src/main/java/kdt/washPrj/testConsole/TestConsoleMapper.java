package kdt.washPrj.testConsole;

import kdt.washPrj.testConsole.dto.TestConsoleStateDTO;
import kdt.washPrj.testConsole.dto.TestConsoleWashTargetDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TestConsoleMapper {

    @Select("""
        SELECT app_api.test_console_customer_exists(CAST(#{customerId} AS bigint))
    """)
    boolean existsCustomer(@Param("customerId") Long customerId);

    @Select("""
        SELECT *
        FROM app_api.get_test_console_state(
            CAST(#{customerId} AS bigint),
            CAST(#{washHistoryId} AS bigint),
            CAST(#{requiredAmount} AS integer)
        )
    """)
    TestConsoleStateDTO selectState(
            @Param("customerId") Long customerId,
            @Param("washHistoryId") Long washHistoryId,
            @Param("requiredAmount") Integer requiredAmount
    );

    @Select("SELECT app_api.billing_card_table_exists()")
    boolean hasBillingCardTable();

    @Select("""
        SELECT app_api.active_billing_card_exists(CAST(#{customerId} AS bigint))
    """)
    boolean hasActiveBillingCard(@Param("customerId") Long customerId);

    @Select("""
        SELECT app_api.auto_pay_billing_card_exists(CAST(#{customerId} AS bigint))
    """)
    boolean hasAutoPayBillingCard(@Param("customerId") Long customerId);

    @Select("SELECT app_api.prepaid_schema_ready()")
    boolean isPrepaidSchemaReady();

    @Select("""
        SELECT *
        FROM app_api.get_test_console_wash_target_for_update(
            CAST(#{washHistoryId} AS bigint)
        )
    """)
    TestConsoleWashTargetDTO selectWashTargetForUpdate(
            @Param("washHistoryId") Long washHistoryId
    );

    @Select("""
        SELECT app_api.test_console_non_member_walk_in_exists(
            CAST(#{washHistoryId} AS bigint)
        )
    """)
    boolean isNonMemberWalkIn(
            @Param("washHistoryId") Long washHistoryId
    );

    @Select("""
        SELECT app_api.cleanup_all_test_console_vehicles()
    """)
    int cleanupAllVehicles();

    @Select("""
        SELECT app_api.create_onsite_payment(
            CAST(#{washHistoryId} AS bigint),
            CAST(#{customerId} AS bigint),
            CAST(#{amount} AS integer),
            CAST(#{paymentType} AS varchar),
            CAST(#{status} AS varchar)
        )
    """)
    Long insertOnsitePayment(
            @Param("washHistoryId") Long washHistoryId,
            @Param("customerId") Long customerId,
            @Param("amount") Integer amount,
            @Param("paymentType") String paymentType,
            @Param("status") String status
    );

    @Select("""
        SELECT app_api.update_wash_payment_status(
            CAST(#{washHistoryId} AS bigint),
            CAST(#{status} AS varchar)
        )
    """)
    int updateWashPaymentStatus(
            @Param("washHistoryId") Long washHistoryId,
            @Param("status") String status
    );
}
