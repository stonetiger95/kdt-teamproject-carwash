package kdt.washPrj.washsite;

import jakarta.validation.Valid;
import kdt.washPrj.washsite.dto.OwnerResponseDTO;
import kdt.washPrj.washsite.dto.WashSiteMapDTO;
import kdt.washPrj.washsite.dto.WashSiteRequestDTO;
import kdt.washPrj.washsite.dto.WashSiteResponseDTO;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class WashSiteController {

    private final WashSiteService washSiteService;

    public WashSiteController(WashSiteService washSiteService) {
        this.washSiteService = washSiteService;
    }

    @GetMapping("/public/wash-sites/map")
    public List<WashSiteMapDTO> getMapWashSites(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) Double radiusKm,
            @RequestParam(required = false) Double south,
            @RequestParam(required = false) Double north,
            @RequestParam(required = false) Double west,
            @RequestParam(required = false) Double east
    ) {
        return washSiteService.getMapWashSites(
                keyword,
                latitude,
                longitude,
                radiusKm,
                south,
                north,
                west,
                east
        );
    }

    @GetMapping("/public/wash-sites/{washSiteId}/map")
    public WashSiteMapDTO getMapWashSite(@PathVariable Long washSiteId) {
        return washSiteService.getMapWashSite(washSiteId);
    }

    @GetMapping("/customer/wash-sites/favorites")
    public List<Long> getCustomerFavoriteWashSiteIds(Authentication authentication) {
        return washSiteService.getCustomerFavoriteWashSiteIds(authentication.getName());
    }

    @PostMapping("/customer/wash-sites/{washSiteId}/favorite")
    public int addCustomerWashSiteFavorite(
            Authentication authentication,
            @PathVariable Long washSiteId
    ) {
        return washSiteService.addCustomerWashSiteFavorite(
                authentication.getName(),
                washSiteId
        );
    }

    @DeleteMapping("/customer/wash-sites/{washSiteId}/favorite")
    public int removeCustomerWashSiteFavorite(
            Authentication authentication,
            @PathVariable Long washSiteId
    ) {
        return washSiteService.removeCustomerWashSiteFavorite(
                authentication.getName(),
                washSiteId
        );
    }

    @GetMapping("/owner/wash-sites")
    public List<WashSiteResponseDTO> getOwnerWashSiteList(Authentication authentication) {
        return washSiteService.getOwnerWashSiteList(authentication.getName());
    }

    @GetMapping("/owner/wash-sites/{washSiteId}")
    public WashSiteResponseDTO getOwnerWashSite(
            Authentication authentication,
            @PathVariable Long washSiteId
    ) {
        return washSiteService.getOwnerWashSite(authentication.getName(), washSiteId);
    }

    @PostMapping("/owner/wash-sites")
    public int createOwnerWashSite(
            Authentication authentication,
            @Valid @RequestBody WashSiteRequestDTO requestDTO
    ) {
        return washSiteService.createOwnerWashSite(authentication.getName(), requestDTO);
    }

    @PatchMapping("/owner/wash-sites/{washSiteId}")
    public int updateOwnerWashSite(
            Authentication authentication,
            @PathVariable Long washSiteId,
            @Valid @RequestBody WashSiteRequestDTO requestDTO
    ) {
        return washSiteService.updateOwnerWashSite(authentication.getName(), washSiteId, requestDTO);
    }

    @GetMapping("/admin/owners")
    public List<OwnerResponseDTO> getOwnerList(Authentication authentication) {
        return washSiteService.getOwnerList(authentication.getName());
    }

    @GetMapping("/admin/owners/{ownerId}/wash-sites")
    public List<WashSiteResponseDTO> getAdminWashSiteListByOwnerId(
            Authentication authentication,
            @PathVariable Long ownerId
    ) {
        return washSiteService.getAdminWashSiteListByOwnerId(
                authentication.getName(),
                ownerId
        );
    }

    @GetMapping("/admin/wash-sites")
    public List<WashSiteResponseDTO> getAdminWashSiteList(Authentication authentication) {
        return washSiteService.getAdminWashSiteList(authentication.getName());
    }

    @GetMapping("/admin/wash-sites/{washSiteId}")
    public WashSiteResponseDTO getAdminWashSite(
            Authentication authentication,
            @PathVariable Long washSiteId
    ) {
        return washSiteService.getAdminWashSite(
                authentication.getName(),
                washSiteId
        );
    }

    @PostMapping("/admin/wash-sites")
    public int createAdminWashSite(
            Authentication authentication,
            @Valid @RequestBody WashSiteRequestDTO requestDTO
    ) {
        return washSiteService.createAdminWashSite(
                authentication.getName(),
                requestDTO
        );
    }

    @PatchMapping("/admin/wash-sites/{washSiteId}")
    public int updateAdminWashSite(
            Authentication authentication,
            @PathVariable Long washSiteId,
            @Valid @RequestBody WashSiteRequestDTO requestDTO
    ) {
        return washSiteService.updateAdminWashSite(
                authentication.getName(),
                washSiteId,
                requestDTO
        );
    }

    // 고객 세차장 조회
    @GetMapping("/customer/wash-sites")
    public List<WashSiteResponseDTO>
    getCustomerWashSiteList() {
        return washSiteService.getCustomerWashSiteList();
    }
}
