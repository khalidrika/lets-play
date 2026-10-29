package com.letsplay.api.service;

import com.letsplay.api.exception.ApiException;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock UserRepository users;
    @Mock ProductRepository products;
    @Mock PasswordEncoder encoder;
    UserService service;

    @BeforeEach
    void setup() { service = new UserService(users, products, encoder); }

    @Test
    void userResponseContainsNoPasswordField() {
        User user = new User("Test", "test@example.com", "private-hash", Role.USER);
        when(users.findById("u1")).thenReturn(Optional.of(user));
        var result = service.get("u1");
        assertEquals("Test", result.name());
        assertEquals(4, result.getClass().getRecordComponents().length);
    }

    @Test
    void deleteUserCleansUpProducts() {
        User user = new User("Test", "test@example.com", "hash", Role.USER);
        when(users.findById("u1")).thenReturn(Optional.of(user));
        service.delete("u1", "admin-id");
        var inOrder = inOrder(products, users);
        inOrder.verify(products).deleteByUserId("u1");
        inOrder.verify(users).delete(user);
    }

    @Test
    void adminCannotDeleteOwnAccount() {
        User user = new User("Admin", "admin@example.com", "hash", Role.ADMIN);
        when(users.findById("admin-id")).thenReturn(Optional.of(user));
        ApiException error = assertThrows(ApiException.class, () -> service.delete("admin-id", "admin-id"));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
        verifyNoInteractions(products);
    }
}
