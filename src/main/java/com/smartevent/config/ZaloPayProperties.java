package com.smartevent.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.payment.zalopay")
public class ZaloPayProperties {
    private String appId;
    private String key1;
    private String key2;
    private String endpoint;
    private String callbackUrl;
}