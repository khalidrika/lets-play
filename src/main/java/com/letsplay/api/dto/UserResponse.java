package com.letsplay.api.dto;

import com.letsplay.api.model.Role;
import com.letsplay.api.model.User;

// Intentionally NEVER exposes the hashed password.
public record UserResponse(String id, String name, String email, Role role) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole());
    }
}
