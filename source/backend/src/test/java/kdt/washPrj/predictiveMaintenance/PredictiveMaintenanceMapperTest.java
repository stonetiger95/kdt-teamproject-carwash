package kdt.washPrj.predictiveMaintenance;

import kdt.washPrj.predictiveMaintenance.dto.SensorReadingDTO;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PredictiveMaintenanceMapperTest {

    private static final String ADMIN_LOGIN_ID =
            System.getProperty("pm.test.admin-login-id", "admin");

    private static final LocalDateTime FIRST_DATA_TIME =
            LocalDateTime.of(2026, 1, 1, 0, 0);

    @Autowired
    private PredictiveMaintenanceMapper mapper;

    @Test
    void findsFirstAndNextCollectedTime() {
        LocalDateTime firstTime = mapper.findFirstCollectedAt(ADMIN_LOGIN_ID);
        LocalDateTime nextTime = mapper.findNextCollectedAt(
                ADMIN_LOGIN_ID,
                firstTime
        );

        assertNotNull(firstTime);
        assertEquals(FIRST_DATA_TIME, firstTime);
        assertEquals(FIRST_DATA_TIME.plusHours(1), nextTime);
    }

    @Test
    void findsAllEquipmentReadingsAtCollectedTime() {
        List<SensorReadingDTO> readings =
                mapper.findReadingsAt(ADMIN_LOGIN_ID, FIRST_DATA_TIME);

        assertEquals(24, readings.size());
        assertTrue(readings.stream().allMatch(reading ->
                FIRST_DATA_TIME.equals(reading.getCollectedAt())
        ));
        assertTrue(readings.stream().anyMatch(reading ->
                "ANT-001".equals(reading.getEquipmentId())
        ));
    }

    @Test
    void mapsSensorColumnsToDtoFields() {
        List<SensorReadingDTO> readings =
                mapper.findReadingsAt(ADMIN_LOGIN_ID, FIRST_DATA_TIME);

        assertFalse(readings.isEmpty());

        SensorReadingDTO reading = readings.get(0);

        assertNotNull(reading.getEquipmentId());
        assertNotNull(reading.getLocationId());
        assertNotNull(reading.getEquipmentType());
        assertNotNull(reading.getSignalStrengthDbm());
        assertNotNull(reading.getSuccessRate());
        assertNotNull(reading.getResponseTimeMs());
        assertNotNull(reading.getPacketLossRate());
    }

    @Test
    void findsEquipmentHistoryOnlyUntilRequestedTime() {
        LocalDateTime currentTime = FIRST_DATA_TIME.plusHours(23);

        List<SensorReadingDTO> history =
                mapper.findReadingsByEquipmentUntil(
                        ADMIN_LOGIN_ID,
                        "ANT-001",
                        currentTime,
                        24
                );

        assertEquals(24, history.size());
        assertTrue(history.stream().allMatch(reading ->
                !reading.getCollectedAt().isAfter(currentTime)
        ));
    }
}
