package com.smartevent.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR", "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR),
    VALIDATION_ERROR("VALIDATION_ERROR", "Invalid request data", HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND("RESOURCE_NOT_FOUND", "Resource not found", HttpStatus.NOT_FOUND),
    ACCESS_DENIED("ACCESS_DENIED", "Access denied", HttpStatus.FORBIDDEN),
    UNAUTHORIZED("UNAUTHORIZED", "Unauthorized", HttpStatus.UNAUTHORIZED),
    BUSINESS_RULE_VIOLATION("BUSINESS_RULE_VIOLATION", "Business rule violation", HttpStatus.BAD_REQUEST),

    // Identity
    USER_NOT_FOUND("USER_NOT_FOUND", "User not found", HttpStatus.NOT_FOUND),
    DUPLICATE_EMAIL("DUPLICATE_EMAIL", "Email already exists", HttpStatus.BAD_REQUEST),
    INVALID_CREDENTIALS("INVALID_CREDENTIALS", "Invalid credentials", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED("TOKEN_EXPIRED", "Token expired", HttpStatus.UNAUTHORIZED),
    ACCOUNT_DISABLED("ACCOUNT_DISABLED", "Account disabled", HttpStatus.UNAUTHORIZED),

    // Event
    EVENT_NOT_FOUND("EVENT_NOT_FOUND", "Event not found", HttpStatus.NOT_FOUND),
    EVENT_NOT_PUBLISHED("EVENT_NOT_PUBLISHED", "Event is not published", HttpStatus.BAD_REQUEST),
    VENUE_TIME_CONFLICT("VENUE_TIME_CONFLICT", "Venue time conflict with another event", HttpStatus.BAD_REQUEST),

    // Ticketing & Inventory (Module 4)
    TICKET_TYPE_NOT_FOUND("TICKET_TYPE_NOT_FOUND", "Ticket type not found", HttpStatus.NOT_FOUND),
    TICKET_TYPE_NAME_EXISTS("TICKET_TYPE_NAME_EXISTS", "Ticket type name already exists for this event", HttpStatus.BAD_REQUEST),
    SALE_PHASE_NOT_FOUND("SALE_PHASE_NOT_FOUND", "Sale phase not found", HttpStatus.NOT_FOUND),
    SALE_PHASE_INVALID_TIME("SALE_PHASE_INVALID_TIME", "Sale end time must be after sale start time", HttpStatus.BAD_REQUEST),
    SALE_PHASE_NOT_ACTIVE("SALE_PHASE_NOT_ACTIVE", "Sale phase is not active for booking", HttpStatus.BAD_REQUEST),
    SALE_PHASE_CLOSED("SALE_PHASE_CLOSED", "Sale phase is closed", HttpStatus.BAD_REQUEST),
    SALE_PHASE_SOLD_OUT("SALE_PHASE_SOLD_OUT", "Sale phase is sold out", HttpStatus.BAD_REQUEST),
    PHASE_CAPACITY_EXCEEDED("PHASE_CAPACITY_EXCEEDED", "Total phase quantity exceeds area capacity", HttpStatus.BAD_REQUEST),
    RULE_ALREADY_EXISTS("RULE_ALREADY_EXISTS", "Phase rule already exists", HttpStatus.BAD_REQUEST),
    INVENTORY_COUNTER_NOT_FOUND("INVENTORY_COUNTER_NOT_FOUND", "Inventory counter not found for sale phase", HttpStatus.NOT_FOUND),
    INVENTORY_NOT_ENOUGH("INVENTORY_NOT_ENOUGH", "Not enough inventory available", HttpStatus.BAD_REQUEST),
    SEAT_ALREADY_HELD("SEAT_ALREADY_HELD", "Seat is already held or sold", HttpStatus.BAD_REQUEST),
    MAX_PER_ORDER_EXCEEDED("MAX_PER_ORDER_EXCEEDED", "Requested quantity exceeds maximum allowed per order", HttpStatus.BAD_REQUEST),
    MAX_PER_USER_EXCEEDED("MAX_PER_USER_EXCEEDED", "Requested quantity exceeds maximum allowed per user", HttpStatus.BAD_REQUEST),
    RESERVATION_EXPIRED("RESERVATION_EXPIRED", "Reservation has expired", HttpStatus.BAD_REQUEST),
    RESERVATION_ALREADY_EXISTS("RESERVATION_ALREADY_EXISTS", "User already has an active pending reservation for this event", HttpStatus.BAD_REQUEST),

    // Ordering (Module 6)
    ORDER_NOT_FOUND("ORDER_NOT_FOUND", "Order not found", HttpStatus.NOT_FOUND),
    ORDER_EXPIRED("ORDER_EXPIRED", "Order has expired", HttpStatus.BAD_REQUEST),
    ORDER_INVALID_STATUS("ORDER_INVALID_STATUS", "Order is not in a valid status for this operation", HttpStatus.BAD_REQUEST),

    // Payment & Ticket (Module 6 & 7)
    PAYMENT_NOT_FOUND("PAYMENT_NOT_FOUND", "Payment record not found", HttpStatus.NOT_FOUND),
    PAYMENT_AMOUNT_MISMATCH("PAYMENT_AMOUNT_MISMATCH", "Payment amount does not match order amount", HttpStatus.BAD_REQUEST),
    PAYMENT_SIGNATURE_INVALID("PAYMENT_SIGNATURE_INVALID", "Invalid payment signature checksum", HttpStatus.BAD_REQUEST),
    PAYMENT_ALREADY_PROCESSED("PAYMENT_ALREADY_PROCESSED", "Payment has already been processed", HttpStatus.BAD_REQUEST),
    PAYMENT_FAILED("PAYMENT_FAILED", "Payment transaction failed", HttpStatus.BAD_REQUEST),
    DUPLICATE_WEBHOOK("DUPLICATE_WEBHOOK", "Webhook event has already been processed", HttpStatus.BAD_REQUEST),
    TICKET_ALREADY_USED("TICKET_ALREADY_USED", "Ticket has already been used", HttpStatus.BAD_REQUEST),
    TICKET_NOT_TRANSFERABLE("TICKET_NOT_TRANSFERABLE", "Ticket cannot be transferred or listed for resale", HttpStatus.BAD_REQUEST),

    // Ticket & Check-in (Module 7)
    TICKET_NOT_FOUND("TICKET_NOT_FOUND", "Ticket record not found", HttpStatus.NOT_FOUND),
    TICKET_INVALID_STATUS("TICKET_INVALID_STATUS", "Ticket is not in a valid status for this operation", HttpStatus.BAD_REQUEST),
    QR_TOKEN_INVALID("QR_TOKEN_INVALID", "Invalid, revoked or expired QR token", HttpStatus.BAD_REQUEST),
    CHECKIN_EVENT_MISMATCH("CHECKIN_EVENT_MISMATCH", "Ticket does not belong to this event", HttpStatus.BAD_REQUEST),


    // Billing & Invoice (Module 8)
    INVOICE_NOT_FOUND("INVOICE_NOT_FOUND", "Invoice record not found", HttpStatus.NOT_FOUND),
    INVOICE_ALREADY_ISSUED("INVOICE_ALREADY_ISSUED", "Invoice has already been issued for this order", HttpStatus.BAD_REQUEST),
    INVOICE_INVALID_STATUS("INVOICE_INVALID_STATUS", "Invoice is not in a valid status for this operation", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;

    ErrorCode(String code, String defaultMessage, HttpStatus httpStatus) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.httpStatus = httpStatus;
    }

    public String code() {
        return code;
    }

    public String defaultMessage() {
        return defaultMessage;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }
}

