package com.smartevent.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.payment.momo")
public class MoMoProperties {
    private String partnerCode;
    private String accessKey;
    private String secretKey;
    private String endpoint;
    private String returnUrl;
    private String ipnUrl;
}