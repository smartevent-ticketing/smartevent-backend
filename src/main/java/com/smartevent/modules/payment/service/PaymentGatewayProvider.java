package com.smartevent.modules.payment.service;

import com.smartevent.common.enums.PaymentMethod;
import com.smartevent.modules.ordering.entity.Order;
import com.smartevent.modules.payment.entity.Payment;
import jakarta.servlet.http.HttpServletRequest;

public interface PaymentGatewayProvider {

    PaymentMethod getPaymentMethod();

    String createPaymentUrl(Payment payment, Order order, HttpServletRequest request, String bankCode);
}