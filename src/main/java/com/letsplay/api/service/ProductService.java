package com.letsplay.api.service;

import com.letsplay.api.dto.ProductRequest;
import com.letsplay.api.dto.ProductResponse;
import com.letsplay.api.exception.ApiException;
import com.letsplay.api.model.Product;
import com.letsplay.api.model.Role;
import com.letsplay.api.model.User;
import com.letsplay.api.repository.ProductRepository;
import com.letsplay.api.repository.UserRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ProductService {
    private final ProductRepository products;
    private final UserRepository users;

    public ProductService(ProductRepository products, UserRepository users) {
        this.products = products;
        this.users = users;
    }

    public List<ProductResponse> listAll() {
        return products.findAll().stream().map(ProductResponse::from).toList();
    }

    public List<ProductResponse> listMine(String currentUserId) {
        return products.findByUserId(currentUserId).stream().map(ProductResponse::from).toList();
    }

    public ProductResponse get(String productId) {
        return ProductResponse.from(find(productId));
    }

    public ProductResponse create(ProductRequest request, String currentUserId) {
        User owner = users.findById(currentUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User account no longer exists"));
        Product product = new Product(request.name().trim(), request.description().trim(),
                request.price(), owner.getId());
        return ProductResponse.from(products.save(product));
    }

    public ProductResponse update(String id, ProductRequest request, String currentUserId) {
        Product product = find(id);
        checkOwnerOrAdmin(product, currentUserId);
        product.setName(request.name().trim());
        product.setDescription(request.description().trim());
        product.setPrice(request.price());
        return ProductResponse.from(products.save(product));
    }

    public void delete(String id, String currentUserId) {
        Product product = find(id);
        checkOwnerOrAdmin(product, currentUserId);
        products.delete(product);
    }

    private Product find(String id) {
        return products.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Product not found"));
    }

    private void checkOwnerOrAdmin(Product product, String userId) {
        User requester = users.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User account no longer exists"));
        if (!product.getUserId().equals(userId) && requester.getRole() != Role.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the owner or an admin may modify this product");
        }
    }
}
