package kdt.washPrj.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/session")
public class SessionStatusController {

    @GetMapping("/status")
    public ResponseEntity<Void> status() {
        return ResponseEntity.noContent().build();
    }
}
