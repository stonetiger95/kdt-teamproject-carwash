package kdt.washPrj.notification;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "line.messaging")
public class LineMessagingProperties {

    private boolean enabled;
    private String channelAccessToken;
    private String channelSecret;
    private String officialAccountUrl;
    private long linkCodeTtlSeconds = 600;
    private String apiUrl = "https://api.line.me/v2/bot/message/push";
    private String replyApiUrl = "https://api.line.me/v2/bot/message/reply";
}
