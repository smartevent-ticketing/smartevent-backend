package com.smartevent.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.payment.paypal")
public class PayPalProperties {
    private String clientId;
    private String secretKey;
    private String baseUrl;
    private String mode = "sandbox";
    private String returnUrl;
    private String cancelUrl;
}