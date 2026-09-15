package kdt.washPrj.payment;

import kdt.washPrj.payment.dto.PaymentProviderOptionDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/public/payments/providers")
public class PaymentProviderController {

    private final PaymentProviderRegistry paymentProviderRegistry;

    public PaymentProviderController(
            PaymentProviderRegistry paymentProviderRegistry
    ) {
        this.paymentProviderRegistry = paymentProviderRegistry;
    }

    @GetMapping
    public List<PaymentProviderOptionDTO> getPaymentProviders() {
        return paymentProviderRegistry.getOptions();
    }
}
