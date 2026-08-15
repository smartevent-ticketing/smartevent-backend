package com.smartevent.ticketing.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR", "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR),
    VALIDATION_ERROR("VALIDATION_ERROR", "Invalid request data", HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND("RESOURCE_NOT_FOUND", "Resource not found", HttpStatus.NOT_FOUND),
    ACCESS_DENIED("ACCESS_DENIED", "Access denied", HttpStatus.FORBIDDEN),
    UNAUTHORIZED("UNAUTHORIZED", "Unauthorized", HttpStatus.UNAUTHORIZED),
    BUSINESS_RULE_VIOLATION("BUSINESS_RULE_VIOLATION", "Business rule violation", HttpStatus.BAD_REQUEST),

    //Identity
    DUPLICATE_EMAIL("DUPLICATE_EMAIL", "Email already exists", HttpStatus.BAD_REQUEST),
    INVALID_CREDENTIALS("INVALID_CREDENTIALS", "Invalid credentials", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED("TOKEN_EXPIRED", "Token expired", HttpStatus.UNAUTHORIZED),
    ACCOUNT_DISABLED("ACCOUNT_DISABLED", "Account disabled", HttpStatus.UNAUTHORIZED),

    /*// Event
EVENT_NOT_PUBLISHED, VENUE_TIME_CONFLICT,
// Booking (Phase quan trọng nhất)
SEAT_ALREADY_HELD, INVENTORY_NOT_ENOUGH, SALE_PHASE_CLOSED,
MAX_PER_ORDER_EXCEEDED, MAX_PER_USER_EXCEEDED,
RESERVATION_EXPIRED, RESERVATION_ALREADY_EXISTS,
// Payment
PAYMENT_AMOUNT_MISMATCH, DUPLICATE_WEBHOOK,
// Ticket
TICKET_ALREADY_USED, TICKET_NOT_TRANSFERABLE*/

    ;

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
