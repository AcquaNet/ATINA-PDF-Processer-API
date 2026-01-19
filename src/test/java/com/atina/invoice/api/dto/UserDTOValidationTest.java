package com.atina.invoice.api.dto;

import com.atina.invoice.api.dto.request.CreateUserRequest;
import com.atina.invoice.api.dto.request.UpdateUserRequest;
import com.atina.invoice.api.dto.response.UserResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitarios para validaciones de DTOs
 */
@DisplayName("DTO Validation Tests")
public class UserDTOValidationTest {

    private static Validator validator;

    @BeforeAll
    public static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Nested
    @DisplayName("CreateUserRequest Validations")
    class CreateUserRequestValidationTests {

        @Test
        @DisplayName("Request válido no tiene violaciones")
        public void testValidRequest_NoViolations() {
            CreateUserRequest request = CreateUserRequest.builder()
                    .username("testuser")
                    .password("password123")
                    .email("test@example.com")
                    .fullName("Test User")
                    .tenantCode("ACME")
                    .role("USER")
                    .enabled(true)
                    .build();

            Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);
            assertTrue(violations.isEmpty());
        }

        @Test
        @DisplayName("Username es requerido")
        public void testUsernameRequired() {
            CreateUserRequest request = CreateUserRequest.builder()
                    .password("password123")
                    .email("test@example.com")
                    .fullName("Test User")
                    .tenantCode("ACME")
                    .build();

            Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("username")));
        }

        @Test
        @DisplayName("Username debe tener mínimo 3 caracteres")
        public void testUsernameMinLength() {
            CreateUserRequest request = CreateUserRequest.builder()
                    .username("ab")  // Solo 2 caracteres
                    .password("password123")
                    .email("test@example.com")
                    .fullName("Test User")
                    .tenantCode("ACME")
                    .build();

            Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);
            assertFalse(violations.isEmpty());
        }

        @Test
        @DisplayName("Password es requerido")
        public void testPasswordRequired() {
            CreateUserRequest request = CreateUserRequest.builder()
                    .username("testuser")
                    .email("test@example.com")
                    .fullName("Test User")
                    .tenantCode("ACME")
                    .build();

            Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("password")));
        }

        @Test
        @DisplayName("Password debe tener mínimo 6 caracteres")
        public void testPasswordMinLength() {
            CreateUserRequest request = CreateUserRequest.builder()
                    .username("testuser")
                    .password("12345")  // Solo 5 caracteres
                    .email("test@example.com")
                    .fullName("Test User")
                    .tenantCode("ACME")
                    .build();

            Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);
            assertFalse(violations.isEmpty());
        }

        @Test
        @DisplayName("Email debe ser válido")
        public void testEmailValidation() {
            CreateUserRequest request = CreateUserRequest.builder()
                    .username("testuser")
                    .password("password123")
                    .email("invalid-email")  // Email inválido
                    .fullName("Test User")
                    .tenantCode("ACME")
                    .build();

            Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("email")));
        }

        @Test
        @DisplayName("FullName es requerido")
        public void testFullNameRequired() {
            CreateUserRequest request = CreateUserRequest.builder()
                    .username("testuser")
                    .password("password123")
                    .email("test@example.com")
                    .tenantCode("ACME")
                    .build();

            Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("fullName")));
        }

        @Test
        @DisplayName("TenantCode es requerido")
        public void testTenantCodeRequired() {
            CreateUserRequest request = CreateUserRequest.builder()
                    .username("testuser")
                    .password("password123")
                    .email("test@example.com")
                    .fullName("Test User")
                    .build();

            Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("tenantCode")));
        }

        @Test
        @DisplayName("Valores por defecto se aplican correctamente")
        public void testDefaultValues() {
            CreateUserRequest request = CreateUserRequest.builder()
                    .username("testuser")
                    .password("password123")
                    .email("test@example.com")
                    .fullName("Test User")
                    .tenantCode("ACME")
                    .role("USER")
                    .enabled(true) // Probar mayúsculas
                    .build();

            assertEquals("USER", request.getRole());
            assertEquals(true, request.getEnabled());
        }
    }

    @Nested
    @DisplayName("UpdateUserRequest Validations")
    class UpdateUserRequestValidationTests {

        @Test
        @DisplayName("Todos los campos son opcionales")
        public void testAllFieldsOptional_NoViolations() {
            UpdateUserRequest request = UpdateUserRequest.builder().build();

            Set<ConstraintViolation<UpdateUserRequest>> violations = validator.validate(request);
            assertTrue(violations.isEmpty());
        }

        @Test
        @DisplayName("Email debe ser válido cuando se proporciona")
        public void testEmailValidationWhenProvided() {
            UpdateUserRequest request = UpdateUserRequest.builder()
                    .email("invalid-email")
                    .build();

            Set<ConstraintViolation<UpdateUserRequest>> violations = validator.validate(request);
            assertFalse(violations.isEmpty());
        }

        @Test
        @DisplayName("Password debe tener mínimo 6 caracteres cuando se proporciona")
        public void testPasswordMinLengthWhenProvided() {
            UpdateUserRequest request = UpdateUserRequest.builder()
                    .password("12345")
                    .build();

            Set<ConstraintViolation<UpdateUserRequest>> violations = validator.validate(request);
            assertFalse(violations.isEmpty());
        }

        @Test
        @DisplayName("Request con campos válidos no tiene violaciones")
        public void testValidRequest_NoViolations() {
            UpdateUserRequest request = UpdateUserRequest.builder()
                    .email("newemail@example.com")
                    .fullName("New Name")
                    .role("ADMIN")
                    .enabled(false)
                    .password("newpassword123")
                    .build();

            Set<ConstraintViolation<UpdateUserRequest>> violations = validator.validate(request);
            assertTrue(violations.isEmpty());
        }
    }

    @Nested
    @DisplayName("UserResponse Tests")
    class UserResponseTests {

        @Test
        @DisplayName("UserResponse se construye correctamente")
        public void testUserResponseBuilder() {
            UserResponse.TenantInfo tenantInfo = UserResponse.TenantInfo.builder()
                    .id(1L)
                    .tenantCode("ACME")
                    .tenantName("ACME Corporation")
                    .build();

            UserResponse response = UserResponse.builder()
                    .id(1L)
                    .username("testuser")
                    .email("test@example.com")
                    .fullName("Test User")
                    .role("USER")
                    .enabled(true)
                    .tenant(tenantInfo)
                    .build();

            assertEquals(1L, response.getId());
            assertEquals("testuser", response.getUsername());
            assertEquals("test@example.com", response.getEmail());
            assertEquals("ACME", response.getTenant().getTenantCode());
        }

        @Test
        @DisplayName("UserResponse no debe incluir password")
        public void testUserResponseNoPassword() {
            UserResponse response = UserResponse.builder()
                    .id(1L)
                    .username("testuser")
                    .email("test@example.com")
                    .build();

            // UserResponse no debe tener campo password
            assertFalse(response.getClass().getDeclaredFields().toString().contains("password"));
        }
    }
}
