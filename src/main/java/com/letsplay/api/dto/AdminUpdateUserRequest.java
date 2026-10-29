package com.letsplay.api.dto;

import com.letsplay.api.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminUpdateUserRequest(
    @NotBlank @Size(min = 2, max = 80) String name,
    @NotBlank @Email @Size(max = 254) String email,
    @Size(min = 8, max = 72) String password,
    @NotNull Role role
) {}
