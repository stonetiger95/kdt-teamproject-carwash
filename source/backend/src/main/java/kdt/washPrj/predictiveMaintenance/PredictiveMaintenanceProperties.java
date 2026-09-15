package kdt.washPrj.predictiveMaintenance;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "predictive-maintenance.api")
public class PredictiveMaintenanceProperties {

    private boolean enabled;
    private String apiUrl;
    private String predictPath;
    private String v2PredictPath;
    private String shadowPredictPath;
    /** Primary dashboard policy. Supported values: v1, v2. */
    private String activeModel = "v2";
    /** Keep the dashboard available while a device builds the v2 history contract. */
    private boolean fallbackToV1 = true;
}
