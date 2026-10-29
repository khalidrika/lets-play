package com.letsplay.api.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProductRequest(
    @NotBlank @Size(min = 2, max = 120) String name,
    @NotBlank @Size(max = 2000) String description,
    @NotNull @DecimalMin(value = "0.0") Double price
) {
    @AssertTrue(message = "price must be a finite number")
    public boolean isPriceFinite() {
        return price != null && Double.isFinite(price);
    }
}
