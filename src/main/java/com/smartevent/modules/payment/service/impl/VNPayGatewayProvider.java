package com.smartevent.modules.payment.service.impl;

import com.smartevent.common.enums.PaymentMethod;
import com.smartevent.common.util.VNPayUtils;
import com.smartevent.config.VNPayProperties;
import com.smartevent.modules.ordering.entity.Order;
import com.smartevent.modules.payment.entity.Payment;
import com.smartevent.modules.payment.service.PaymentGatewayProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class VNPayGatewayProvider implements PaymentGatewayProvider {

    private final VNPayProperties vnpayProperties;

    @Override
    public PaymentMethod getPaymentMethod() {
        return PaymentMethod.VNPAY;
    }

    @Override
    public String createPaymentUrl(Payment payment, Order order, HttpServletRequest request, String bankCode) {
        long amountInCents = payment.getAmount().multiply(BigDecimal.valueOf(100)).longValue();

        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        String createDate = now.format(formatter);
        String expireDate = now.plusMinutes(15).format(formatter);

        Map<String, String> vnpParams = new HashMap<>();
        vnpParams.put("vnp_Version", vnpayProperties.getVersion());
        vnpParams.put("vnp_Command", vnpayProperties.getCommand());
        vnpParams.put("vnp_TmnCode", vnpayProperties.getTmnCode());
        vnpParams.put("vnp_Amount", String.valueOf(amountInCents));
        vnpParams.put("vnp_CurrCode", "VND");
        vnpParams.put("vnp_TxnRef", order.getOrderCode());
        vnpParams.put("vnp_OrderInfo", "Thanh toan don hang " + order.getOrderCode());
        vnpParams.put("vnp_OrderType", "other");
        vnpParams.put("vnp_Locale", "vn");
        vnpParams.put("vnp_ReturnUrl", vnpayProperties.getReturnUrl());
        vnpParams.put("vnp_IpAddr", VNPayUtils.getIpAddress(request));
        vnpParams.put("vnp_CreateDate", createDate);
        vnpParams.put("vnp_ExpireDate", expireDate);

        if (bankCode != null && !bankCode.isBlank()) {
            vnpParams.put("vnp_BankCode", bankCode);
        }

        String queryString = VNPayUtils.buildQueryUrl(vnpParams, vnpayProperties.getHashSecret());
        String paymentUrl = vnpayProperties.getPayUrl() + "?" + queryString;

        log.info("Khởi tạo Pay URL VNPay thành công cho đơn hàng {}: {}", order.getOrderCode(), paymentUrl);
        return paymentUrl;
    }
}
