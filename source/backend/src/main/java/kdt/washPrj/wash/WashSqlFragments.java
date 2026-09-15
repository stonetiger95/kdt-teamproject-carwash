package kdt.washPrj.wash;

final class WashSqlFragments {

    static final String HISTORY_RESPONSE_COLUMNS = """
        wh.wash_history_id,
        wh.customer_id,
        wh.vehicle_id,
        wh.wash_site_id,
        wh.course_id,
        ws.site_name,
        ws.site_phone,
        wc.course_name,
            COALESCE(
                wh.wash_price,
                wc.price
            ) AS amount,
        wh.plate_number,
        wh.entry_time,
        wh.wash_start_time,
        wh.wash_end_time,
        wh.exit_time,
        wh.customer_type,
        CASE
            WHEN wh.payment_status = 'READY' THEN 'WAITING'
            WHEN wh.payment_status = 'DONE' THEN 'SUCCESS'
            ELSE wh.payment_status
        END AS payment_status,
        CASE
            WHEN wh.wash_status = 'WAITING' THEN 'ENTERED'
            ELSE wh.wash_status
        END AS wash_status
        """;

    static final String HISTORY_SEARCH_FILTERS = """
        AND (
            CAST(#{search.washSiteId} AS BIGINT) IS NULL
            OR wh.wash_site_id = CAST(#{search.washSiteId} AS BIGINT)
        )
        AND (
            CAST(#{search.plateNumber} AS VARCHAR) IS NULL
            OR wh.plate_number ILIKE CONCAT(
                '%',
                CAST(#{search.plateNumber} AS VARCHAR),
                '%'
            )
        )
        AND (
            CAST(#{search.customerType} AS VARCHAR) IS NULL
            OR wh.customer_type = CAST(#{search.customerType} AS VARCHAR)
        )
        AND (
            CAST(#{search.washStatus} AS VARCHAR) IS NULL
            OR (
                CAST(#{search.washStatus} AS VARCHAR) = 'ENTERED'
                AND wh.wash_status IN ('ENTERED', 'WAITING')
            )
            OR wh.wash_status = CAST(#{search.washStatus} AS VARCHAR)
        )
        AND (
            CAST(#{search.paymentStatus} AS VARCHAR) IS NULL
            OR (
                CAST(#{search.paymentStatus} AS VARCHAR) = 'WAITING'
                AND wh.payment_status IN ('WAITING', 'READY')
            )
            OR (
                CAST(#{search.paymentStatus} AS VARCHAR) = 'SUCCESS'
                AND wh.payment_status IN ('SUCCESS', 'DONE')
            )
            OR wh.payment_status = CAST(#{search.paymentStatus} AS VARCHAR)
        )
        AND (
            CAST(#{search.startDate} AS DATE) IS NULL
            OR wh.entry_time >= CAST(#{search.startDate} AS DATE)
        )
        AND (
            CAST(#{search.endDate} AS DATE) IS NULL
            OR wh.entry_time < CAST(#{search.endDate} AS DATE) + INTERVAL '1 day'
        )
        """;

    static final String HISTORY_ADMIN_OWNER_FILTER = """
        AND (
            CAST(#{search.ownerId} AS BIGINT) IS NULL
            OR ws.owner_id = CAST(#{search.ownerId} AS BIGINT)
        )
        """;

    private WashSqlFragments() {
    }
}
