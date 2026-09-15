package kdt.washPrj.settlement;

import kdt.washPrj.settlement.dto.SettlementSiteComparisonDTO;
import kdt.washPrj.settlement.dto.SettlementSummaryDTO;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

@Service
public class SettlementService {

    private final SettlementMapper settlementMapper;
    private final JsonMapper jsonMapper;

    public SettlementService(
            SettlementMapper settlementMapper,
            JsonMapper jsonMapper
    ) {
        this.settlementMapper = settlementMapper;
        this.jsonMapper = jsonMapper;
    }

    public SettlementSummaryDTO getOwnerDashboard(
            String loginId,
            String periodType,
            String startDate,
            String endDate,
            Long washSiteId
    ) {
        return getDashboard(loginId, null, washSiteId, periodType, startDate, endDate);
    }

    public SettlementSummaryDTO getAdminDashboard(
            String loginId,
            String periodType,
            String startDate,
            String endDate,
            Long ownerId,
            Long washSiteId
    ) {
        return getDashboard(loginId, ownerId, washSiteId, periodType, startDate, endDate);
    }

    public List<SettlementSiteComparisonDTO> getAdminSiteComparisons(
            String loginId,
            String startDate,
            String endDate,
            Long ownerId,
            Long washSiteId
    ) {
        LocalDate end = parseDateOrDefault(endDate, LocalDate.now());
        LocalDate start = parseDateOrDefault(startDate, end.minusDays(29));

        if (start.isAfter(end)) {
            LocalDate temp = start;
            start = end;
            end = temp;
        }

        return settlementMapper.selectSiteComparisons(
                loginId,
                ownerId,
                washSiteId,
                start.toString(),
                end.toString()
        );
    }

    private SettlementSummaryDTO getDashboard(
            String loginId,
            Long ownerId,
            Long washSiteId,
            String periodType,
            String startDate,
            String endDate
    ) {
        String normalizedPeriodType = normalizePeriodType(periodType);
        LocalDate end = parseDateOrDefault(endDate, LocalDate.now());
        LocalDate start = parseDateOrDefault(startDate, end.minusDays(29));

        if (start.isAfter(end)) {
            LocalDate temp = start;
            start = end;
            end = temp;
        }

        String dashboardJson = settlementMapper.selectDashboardBundle(
                loginId,
                ownerId,
                washSiteId,
                normalizedPeriodType,
                start.toString(),
                end.toString()
        );
        SettlementSummaryDTO summary = readDashboard(dashboardJson);

        summary.setPeriodType(normalizedPeriodType);
        summary.setStartDate(start.toString());
        summary.setEndDate(end.toString());

        normalizeEmptyValues(summary);
        return summary;
    }

    private SettlementSummaryDTO readDashboard(String dashboardJson) {
        if (dashboardJson == null || dashboardJson.isBlank()) {
            return new SettlementSummaryDTO();
        }

        try {
            return jsonMapper.readValue(dashboardJson, SettlementSummaryDTO.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to read settlement dashboard data.", exception);
        }
    }

    private String normalizePeriodType(String periodType) {
        if (periodType == null || periodType.isBlank()) {
            return "DAILY";
        }

        String upperPeriodType = periodType.toUpperCase();
        if (List.of("DAILY", "WEEKLY", "MONTHLY").contains(upperPeriodType)) {
            return upperPeriodType;
        }

        return "DAILY";
    }

    private LocalDate parseDateOrDefault(String date, LocalDate defaultDate) {
        if (date == null || date.isBlank()) {
            return defaultDate;
        }

        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException exception) {
            return defaultDate;
        }
    }

    private void normalizeEmptyValues(SettlementSummaryDTO summary) {
        summary.setTotalUseCount(safe(summary.getTotalUseCount()));
        summary.setUniqueCustomerCount(safe(summary.getUniqueCustomerCount()));
        summary.setMemberUseCount(safe(summary.getMemberUseCount()));
        summary.setNonMemberUseCount(safe(summary.getNonMemberUseCount()));
        summary.setTotalUseAmount(safe(summary.getTotalUseAmount()));
        summary.setTotalPaidAmount(safe(summary.getTotalPaidAmount()));
        summary.setWaitingPaymentCount(safe(summary.getWaitingPaymentCount()));
        summary.setSuccessPaymentCount(safe(summary.getSuccessPaymentCount()));
        summary.setFailedPaymentCount(safe(summary.getFailedPaymentCount()));
        summary.setCanceledPaymentCount(safe(summary.getCanceledPaymentCount()));
        summary.setAbnormalLogCount(safe(summary.getAbnormalLogCount()));
        summary.setConversionCount(safe(summary.getConversionCount()));
        summary.setConversionRate(summary.getConversionRate() == null ? 0.0 : summary.getConversionRate());
    }

    private Long safe(Long value) {
        return value == null ? 0L : value;
    }
}
