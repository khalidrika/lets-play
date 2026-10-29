package com.letsplay.api.service;

import com.letsplay.api.dto.AdminCreateUserRequest;
import com.letsplay.api.dto.AdminUpdateUserRequest;
import com.letsplay.api.dto.UserResponse;
import com.letsplay.api.exception.ApiException;
import com.letsplay.api.model.Role;
import com.letsplay.api.model.User;
import com.letsplay.api.repository.ProductRepository;
import com.letsplay.api.repository.UserRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    private final UserRepository users;
    private final ProductRepository products;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository users, ProductRepository products, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.products = products;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UserResponse> listAll() {
        return users.findAll().stream().map(UserResponse::from).toList();
    }

    public UserResponse get(String id) {
        return UserResponse.from(find(id));
    }

    public UserResponse create(AdminCreateUserRequest request) {
        String email = AuthService.normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email is already registered");
        }
        User user = users.save(new User(request.name().trim(), email,
                passwordEncoder.encode(request.password()), request.role()));
        return UserResponse.from(user);
    }

    public UserResponse update(String id, AdminUpdateUserRequest request, String currentAdminId) {
        User user = find(id);
        String email = AuthService.normalizeEmail(request.email());
        if (!email.equals(user.getEmail()) && users.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email is already registered");
        }
        if (id.equals(currentAdminId) && request.role() != Role.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You cannot demote your own admin account");
        }
        if (user.getRole() == Role.ADMIN && request.role() != Role.ADMIN &&
                users.countByRole(Role.ADMIN) <= 1) {
            throw new ApiException(HttpStatus.CONFLICT, "Cannot demote the last admin");
        }
        user.setName(request.name().trim());
        user.setEmail(email);
        user.setRole(request.role());
        if (request.password() != null) {
            user.setPassword(passwordEncoder.encode(request.password()));
        }
        return UserResponse.from(users.save(user));
    }

    public void delete(String id, String currentAdminId) {
        User user = find(id);
        if (id.equals(currentAdminId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You cannot delete your own admin account");
        }
        if (user.getRole() == Role.ADMIN && users.countByRole(Role.ADMIN) <= 1) {
            throw new ApiException(HttpStatus.CONFLICT, "Cannot delete the last admin");
        }
        // MongoDB standalone does not support multi-document transactions.
        products.deleteByUserId(id);
        users.delete(user);
    }

    private User find(String id) {
        return users.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
