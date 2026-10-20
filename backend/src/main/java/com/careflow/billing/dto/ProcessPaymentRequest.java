package com.careflow.billing.dto;

import com.careflow.billing.entity.PaymentMethod;
import jakarta.validation.constraints.NotNull;

public record ProcessPaymentRequest(
        @NotNull(message = "Payment method is required")
        PaymentMethod method
) {}
