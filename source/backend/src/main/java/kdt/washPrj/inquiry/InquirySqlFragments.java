package kdt.washPrj.inquiry;

final class InquirySqlFragments {

    static final String RESPONSE_COLUMNS = """
        i.inquiry_id,
        i.title,
        i.content,
        i.writer_id,
        i.writer_name,
        i.writer_role,
        i.status,
        i.answer,
        CASE
            WHEN i.payment_history_id IS NOT NULL THEN 'REFUND'
            WHEN i.wash_site_id IS NOT NULL THEN 'WASH_SITE'
            ELSE 'ETC'
        END AS inquiry_type,
        i.payment_history_id,
        ph.purpose AS payment_purpose,
        ph.amount AS payment_amount,
        COALESCE(refund.total_refunded, 0) AS refunded_amount,
        CASE
            WHEN ph.payment_history_id IS NULL THEN NULL
            WHEN ph.purpose = 'CHARGE' THEN GREATEST(
                LEAST(
                    ph.amount - COALESCE(refund.total_refunded, 0),
                    COALESCE(pw.balance, 0)
                ),
                0
            )
            ELSE GREATEST(
                ph.amount - COALESCE(refund.total_refunded, 0),
                0
            )
        END AS refundable_amount,
        i.wash_site_id,
        ws.site_name,
        ws.address AS site_address,
        ws.site_phone,
        (
            SELECT COUNT(*)::integer
            FROM inquiry_answer ia
            WHERE ia.inquiry_id = i.inquiry_id
        ) AS answer_count,
        i.created_at,
        i.updated_at,
        i.answered_at
        """;

    static final String RESPONSE_FROM = """
        FROM inquiry i
        LEFT JOIN wash_site ws
          ON ws.wash_site_id = i.wash_site_id
        LEFT JOIN payment_history ph
          ON ph.payment_history_id = i.payment_history_id
        LEFT JOIN prepaid_charge_history pch
          ON pch.payment_history_id = ph.payment_history_id
        LEFT JOIN prepaid_wallet pw
          ON pw.wallet_id = pch.wallet_id
        LEFT JOIN LATERAL (
            SELECT COALESCE(SUM(prh.refund_amount), 0)::integer AS total_refunded
            FROM payment_refund_history prh
            WHERE prh.payment_history_id = ph.payment_history_id
              AND prh.status IN ('WAITING', 'SUCCESS')
        ) refund ON TRUE
        """;

    private InquirySqlFragments() {
    }
}
