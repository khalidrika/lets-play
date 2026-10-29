package com.letsplay.api.dto;

import com.letsplay.api.model.Product;

public record ProductResponse(String id, String name, String description, Double price, String userId) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(product.getId(), product.getName(), product.getDescription(),
                product.getPrice(), product.getUserId());
    }
}
