package kdt.washPrj.predictiveMaintenance;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "telegram")
public class TelegramProperties {

    private boolean enabled;
    private String botToken;
    private String chatId;
}
