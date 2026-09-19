package com.maxx_global.enums;

import org.springframework.http.HttpStatus;

public enum ApiErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "error.validation"),
    AUTHENTICATION_FAILED(HttpStatus.UNAUTHORIZED, "error.authentication"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "error.access_denied"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "error.resource_not_found"),
    PRODUCT_VARIANT_NOT_FOUND(HttpStatus.NOT_FOUND, "error.product_variant_not_found"),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "error.order_not_found"),
    CART_NOT_FOUND(HttpStatus.NOT_FOUND, "error.cart_not_found"),
    INSUFFICIENT_STOCK(HttpStatus.UNPROCESSABLE_ENTITY, "error.insufficient_stock"),
    PRODUCT_VARIANT_INACTIVE(HttpStatus.UNPROCESSABLE_ENTITY, "error.product_variant_inactive"),
    PRODUCT_INACTIVE(HttpStatus.UNPROCESSABLE_ENTITY, "error.product_inactive"),
    PRICE_MISMATCH(HttpStatus.UNPROCESSABLE_ENTITY, "error.price_mismatch"),
    PRICE_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "error.price_invalid"),
    DEALER_MISMATCH(HttpStatus.UNPROCESSABLE_ENTITY, "error.dealer_mismatch"),
    CART_EMPTY(HttpStatus.UNPROCESSABLE_ENTITY, "error.cart_empty"),
    INVALID_ORDER(HttpStatus.UNPROCESSABLE_ENTITY, "error.invalid_order"),
    INVALID_QUANTITY(HttpStatus.UNPROCESSABLE_ENTITY, "error.invalid_quantity"),
    CONFLICT(HttpStatus.CONFLICT, "error.conflict"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "error.internal");

    private final HttpStatus status;
    private final String messageKey;

    ApiErrorCode(HttpStatus status, String messageKey) {
        this.status = status;
        this.messageKey = messageKey;
    }

    public String getCode() { return name(); }
    public HttpStatus getStatus() { return status; }
    public String getMessageKey() { return messageKey; }
}
