package kdt.washPrj.prepaid;

import kdt.washPrj.prepaid.dto.BillingCardDTO;
import kdt.washPrj.prepaid.dto.PrepaidWalletDTO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PrepaidQueryService {

    private final PrepaidMapper prepaidMapper;

    public PrepaidQueryService(PrepaidMapper prepaidMapper) {
        this.prepaidMapper = prepaidMapper;
    }

    public PrepaidWalletDTO getWallet(String loginId) {
        return prepaidMapper.selectAuthorizedWallet(loginId);
    }

    public List<BillingCardDTO> getBillingCards(
            String loginId,
            String role
    ) {
        return prepaidMapper.selectAuthorizedBillingCards(loginId);
    }
}
