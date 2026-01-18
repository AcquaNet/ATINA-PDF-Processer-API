package com.atina.invoice.api.dto.request;


import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para actualizar un usuario existente
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRequest {

    @Email(message = "Email must be valid")
    private String email;

    @Size(min = 1, max = 255, message = "Full name must be between 1 and 255 characters")
    private String fullName;

    private String role;

    private Boolean enabled;

    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;  // Optional: solo si quiere cambiar password
}
