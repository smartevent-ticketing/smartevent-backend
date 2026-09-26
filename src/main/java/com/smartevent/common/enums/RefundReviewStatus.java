package com.smartevent.common.enums;

/** Manual reconciliation state. No value in this enum triggers a provider refund. */
public enum RefundReviewStatus {
    REQUIRED,
    IN_REVIEW,
    REFUNDED_CONFIRMED,
    CLOSED_NO_REFUND
}
