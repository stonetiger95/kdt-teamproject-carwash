package kdt.washPrj.memberManage;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import kdt.washPrj.global.pagenation.PageResponseDTO;
import kdt.washPrj.memberManage.dto.MemberManageDetailDTO;
import kdt.washPrj.memberManage.dto.MemberManageListDTO;
import kdt.washPrj.memberManage.dto.MemberManageSearchDTO;
import kdt.washPrj.memberManage.dto.MemberUpdateDTO;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/admin/member-manage")
public class MemberManageController {
    @Resource
    MemberManageService service;

    @GetMapping
    public PageResponseDTO<MemberManageListDTO> memberList(
            Authentication authentication,
            @Valid MemberManageSearchDTO searchDTO
    ){
        return service.memberList(authentication.getName(), searchDTO);
    }

    @GetMapping("/{customerId}")
    public MemberManageDetailDTO memberDetail(
            Authentication authentication,
            @PathVariable Integer customerId
    ){
        return service.memberDetail(authentication.getName(), customerId);
    }

    @PatchMapping("/{customerId}")
    public MemberManageDetailDTO updateMemberStatus(
            Authentication authentication,
            @PathVariable Integer customerId,
            @Valid @RequestBody MemberUpdateDTO request
    ) {
        return service.updateMember(authentication.getName(), customerId, request);
    }
}
