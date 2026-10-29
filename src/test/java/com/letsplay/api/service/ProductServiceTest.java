package com.letsplay.api.service;

import com.letsplay.api.dto.ProductRequest;
import com.letsplay.api.exception.ApiException;
import com.letsplay.api.model.Product;
import com.letsplay.api.model.Role;
import com.letsplay.api.model.User;
import com.letsplay.api.repository.ProductRepository;
import com.letsplay.api.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {
    @Mock ProductRepository products;
    @Mock UserRepository users;
    ProductService service;

    @BeforeEach
    void init() { service = new ProductService(products, users); }

    @Test
    void differentUserCannotUpdateOrDeleteProduct() {
        Product product = new Product("Console", "Gaming", 320.0, "owner-id");
        User visitor = new User("Visitor", "visitor@example.com", "hash", Role.USER);
        when(products.findById("p1")).thenReturn(Optional.of(product));
        when(users.findById("other-id")).thenReturn(Optional.of(visitor));

        ApiException update = assertThrows(ApiException.class, () ->
                service.update("p1", new ProductRequest("New", "Updated", 22.0), "other-id"));
        ApiException delete = assertThrows(ApiException.class, () -> service.delete("p1", "other-id"));
        assertEquals(HttpStatus.FORBIDDEN, update.getStatus());
        assertEquals(HttpStatus.FORBIDDEN, delete.getStatus());
        verify(products, never()).save(any());
        verify(products, never()).delete(any());
    }

    @Test
    void adminCanUpdateSomebodyElsesProduct() {
        Product product = new Product("Old", "Old description", 12.0, "owner-id");
        User admin = new User("Admin", "admin@example.com", "hash", Role.ADMIN);
        when(products.findById("p1")).thenReturn(Optional.of(product));
        when(users.findById("admin-id")).thenReturn(Optional.of(admin));
        when(products.save(any(Product.class))).thenAnswer(call -> call.getArgument(0));

        var updated = service.update("p1", new ProductRequest("New", "New description", 30.5), "admin-id");
        assertEquals("New", updated.name());
        assertEquals("owner-id", updated.userId());
    }

    @Test
    void newProductAlwaysBelongsToAuthenticatedUser() {
        User owner = new User("Owner", "owner@example.com", "hash", Role.USER);
        owner.setId("owner-id");
        when(users.findById("owner-id")).thenReturn(Optional.of(owner));
        when(products.save(any(Product.class))).thenAnswer(call -> call.getArgument(0));

        var created = service.create(new ProductRequest("Item", "Description", 9.0), "owner-id");
        assertEquals("owner-id", created.userId());
    }

    @Test
    void missingProductReturns404() {
        when(products.findById("missing")).thenReturn(Optional.empty());
        ApiException error = assertThrows(ApiException.class, () -> service.get("missing"));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatus());
    }
}
