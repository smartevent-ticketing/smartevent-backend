package com.smartevent.common.enums;

public enum OutboxStatus {
    PENDING,    // Đang chờ tiến trình Worker quét và đẩy vào RabbitMQ
    PUBLISHED,  // Đã đẩy vào RabbitMQ thành công
    FAILED      // Đẩy thất bại sau nhiều lần thử lại
}