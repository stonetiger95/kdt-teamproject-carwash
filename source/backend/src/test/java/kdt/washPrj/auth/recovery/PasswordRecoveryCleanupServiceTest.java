package kdt.washPrj.auth.recovery;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyInt;

class PasswordRecoveryCleanupServiceTest {

    @Test
    void cleanupUsesConfiguredRetentionWhenFeatureIsEnabled() {
        PasswordRecoveryProperties properties =
                mock(PasswordRecoveryProperties.class);
        PasswordRecoveryMapper mapper = mock(PasswordRecoveryMapper.class);
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getCleanupRetentionHours()).thenReturn(24);

        new PasswordRecoveryCleanupService(properties, mapper)
                .cleanupExpiredRecoveryData();

        verify(mapper).cleanup(24);
    }

    @Test
    void cleanupDoesNothingWhenFeatureIsDisabled() {
        PasswordRecoveryProperties properties =
                mock(PasswordRecoveryProperties.class);
        PasswordRecoveryMapper mapper = mock(PasswordRecoveryMapper.class);
        when(properties.isEnabled()).thenReturn(false);

        new PasswordRecoveryCleanupService(properties, mapper)
                .cleanupExpiredRecoveryData();

        verify(mapper, never()).cleanup(anyInt());
    }
}
