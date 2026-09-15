package kdt.washPrj.businessHour;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/owner/wash-sites")
public class BusinessHourController {

    private final BusinessHourService businessHourService;

    public BusinessHourController(
            BusinessHourService businessHourService
    ) {
        this.businessHourService = businessHourService;
    }

    @GetMapping("/{washSiteId}/business-hours")
    public List<BusinessHourDTO> getBusinessHours(
            Authentication authentication,
            @PathVariable Long washSiteId
    ) {
        return businessHourService.getOwnerBusinessHours(
                authentication.getName(),
                washSiteId
        );
    }

    @PutMapping("/{washSiteId}/business-hours")
    public ResponseEntity<Void> updateBusinessHours(
            Authentication authentication,
            @PathVariable Long washSiteId,
            @Valid @RequestBody List<@Valid BusinessHourDTO> businessHours
    ) {
        businessHourService.updateOwnerBusinessHours(
                authentication.getName(),
                washSiteId,
                businessHours
        );

        return ResponseEntity.noContent().build();
    }
}
