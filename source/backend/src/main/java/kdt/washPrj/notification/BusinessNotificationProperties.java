package kdt.washPrj.notification;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "business-notification.dispatcher")
public class BusinessNotificationProperties {

    private boolean enabled;
    private int batchSize = 20;
    private int leaseSeconds = 120;
    private int retrySeconds = 60;
    private int maxAttempts = 5;
}
