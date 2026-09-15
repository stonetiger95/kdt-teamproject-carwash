package kdt.washPrj.notification;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/line/webhook")
public class LineWebhookController {

    private final LineWebhookService webhookService;

    public LineWebhookController(LineWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> webhook(
            @RequestHeader(name = "x-line-signature", required = false) String signature,
            @RequestBody String rawBody
    ) {
        webhookService.handle(rawBody, signature);
        return ResponseEntity.ok().build();
    }
}
