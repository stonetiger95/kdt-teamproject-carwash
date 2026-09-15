package kdt.washPrj.businessHour;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BusinessHourServiceTest {

    private BusinessHourMapper businessHourMapper;
    private BusinessHourService businessHourService;

    @BeforeEach
    void setUp() {
        businessHourMapper = mock(BusinessHourMapper.class);
        businessHourService = new BusinessHourService(
                businessHourMapper
        );
    }

    @Test
    void getOwnerBusinessHoursChecksOwnership() {
        List<BusinessHourDTO> expected = weeklyBusinessHours();
        when(businessHourMapper.existsOwnerWashSite(
                "owner01", 3L
        )).thenReturn(true);
        when(businessHourMapper.selectBusinessHours("owner01", 3L))
                .thenReturn(expected);

        List<BusinessHourDTO> result =
                businessHourService.getOwnerBusinessHours(
                        "owner01", 3L
                );

        assertSame(expected, result);
    }

    @Test
    void updateOwnerBusinessHoursSavesSevenDays() {
        List<BusinessHourDTO> businessHours = weeklyBusinessHours();
        when(businessHourMapper.existsOwnerWashSite(
                "owner01", 3L
        )).thenReturn(true);
        when(businessHourMapper.upsertBusinessHour(
                eq("owner01"), eq(3L), any(BusinessHourDTO.class)
        )).thenReturn(1);

        businessHourService.updateOwnerBusinessHours(
                "owner01", 3L, businessHours
        );

        verify(businessHourMapper, times(7))
                .upsertBusinessHour(
                        eq("owner01"),
                        eq(3L),
                        any(BusinessHourDTO.class)
                );
    }

    @Test
    void updateOwnerBusinessHoursClearsTimesForClosedDay() {
        List<BusinessHourDTO> businessHours = weeklyBusinessHours();
        BusinessHourDTO sunday = businessHours.get(6);
        sunday.setClosed(true);
        sunday.setOpenTime(LocalTime.of(9, 0));
        sunday.setCloseTime(LocalTime.of(18, 0));
        when(businessHourMapper.existsOwnerWashSite(
                "owner01", 3L
        )).thenReturn(true);
        when(businessHourMapper.upsertBusinessHour(
                eq("owner01"), eq(3L), any(BusinessHourDTO.class)
        )).thenReturn(1);

        businessHourService.updateOwnerBusinessHours(
                "owner01", 3L, businessHours
        );

        assertNull(sunday.getOpenTime());
        assertNull(sunday.getCloseTime());
    }

    @Test
    void updateOwnerBusinessHoursRejectsMissingDay() {
        List<BusinessHourDTO> businessHours = weeklyBusinessHours();
        businessHours.remove(6);
        when(businessHourMapper.existsOwnerWashSite(
                "owner01", 3L
        )).thenReturn(true);

        assertBadRequest(businessHours);
        verify(businessHourMapper, never())
                .upsertBusinessHour(anyString(), anyLong(), any());
    }

    @Test
    void updateOwnerBusinessHoursRejectsDuplicateDay() {
        List<BusinessHourDTO> businessHours = weeklyBusinessHours();
        businessHours.get(6).setDayOfWeek(1);
        when(businessHourMapper.existsOwnerWashSite(
                "owner01", 3L
        )).thenReturn(true);

        assertBadRequest(businessHours);
    }

    @Test
    void updateOwnerBusinessHoursRejectsClosedAndTwentyFourHours() {
        List<BusinessHourDTO> businessHours = weeklyBusinessHours();
        businessHours.get(0).setClosed(true);
        businessHours.get(0).setTwentyFourHours(true);
        when(businessHourMapper.existsOwnerWashSite(
                "owner01", 3L
        )).thenReturn(true);

        assertBadRequest(businessHours);
    }

    @Test
    void updateOwnerBusinessHoursRejectsInvalidNormalTime() {
        List<BusinessHourDTO> businessHours = weeklyBusinessHours();
        businessHours.get(0).setOpenTime(LocalTime.of(22, 0));
        businessHours.get(0).setCloseTime(LocalTime.of(9, 0));
        when(businessHourMapper.existsOwnerWashSite(
                "owner01", 3L
        )).thenReturn(true);

        assertBadRequest(businessHours);
    }

    @Test
    void updateOwnerBusinessHoursRejectsAnotherOwnersSite() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> businessHourService.updateOwnerBusinessHours(
                        "owner01",
                        3L,
                        weeklyBusinessHours()
                )
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verify(businessHourMapper, never())
                .upsertBusinessHour(anyString(), anyLong(), any());
    }

    @Test
    void createDefaultBusinessHoursRequiresSevenRows() {
        when(businessHourMapper.insertDefaultBusinessHours("owner01", 3L))
                .thenReturn(7);
        assertDoesNotThrow(
                () -> businessHourService.createDefaultBusinessHours("owner01", 3L)
        );

        when(businessHourMapper.insertDefaultBusinessHours("owner01", 4L))
                .thenReturn(6);
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> businessHourService.createDefaultBusinessHours("owner01", 4L)
        );
        assertEquals(
                HttpStatus.INTERNAL_SERVER_ERROR,
                exception.getStatusCode()
        );
    }

    private void assertBadRequest(
            List<BusinessHourDTO> businessHours
    ) {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> businessHourService.updateOwnerBusinessHours(
                        "owner01", 3L, businessHours
                )
        );
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    private List<BusinessHourDTO> weeklyBusinessHours() {
        List<BusinessHourDTO> businessHours = new ArrayList<>();

        for (int dayOfWeek = 1; dayOfWeek <= 7; dayOfWeek++) {
            BusinessHourDTO businessHour = new BusinessHourDTO();
            businessHour.setDayOfWeek(dayOfWeek);
            businessHour.setOpenTime(LocalTime.of(9, 0));
            businessHour.setCloseTime(LocalTime.of(22, 0));
            businessHour.setClosed(false);
            businessHour.setTwentyFourHours(false);
            businessHours.add(businessHour);
        }

        return businessHours;
    }
}
