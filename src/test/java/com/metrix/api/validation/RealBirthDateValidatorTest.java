package com.metrix.api.validation;

import com.metrix.api.dto.CreateUserRequest;
import com.metrix.api.dto.UpdateUserRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RealBirthDateValidatorTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void create_request_rejects_future_birth_date() {
        CreateUserRequest request = new CreateUserRequest();
        request.setFechaNacimiento(LocalDate.now().plusDays(1));

        Set<ConstraintViolation<CreateUserRequest>> violations = validator.validateProperty(request, "fechaNacimiento");

        assertFalse(violations.isEmpty());
    }

    @Test
    void update_request_rejects_unrealistic_birth_date() {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setFechaNacimiento(LocalDate.now().minusYears(121));

        Set<ConstraintViolation<UpdateUserRequest>> violations = validator.validateProperty(request, "fechaNacimiento");

        assertFalse(violations.isEmpty());
    }

    @Test
    void requests_accept_today_and_recent_past_birth_dates() {
        UpdateUserRequest todayUpdateRequest = new UpdateUserRequest();
        todayUpdateRequest.setFechaNacimiento(LocalDate.now());

        CreateUserRequest createRequest = new CreateUserRequest();
        createRequest.setFechaNacimiento(LocalDate.now().minusYears(35));

        UpdateUserRequest pastUpdateRequest = new UpdateUserRequest();
        pastUpdateRequest.setFechaNacimiento(LocalDate.now().minusYears(35));

        assertTrue(validator.validateProperty(createRequest, "fechaNacimiento").isEmpty());
        assertTrue(validator.validateProperty(todayUpdateRequest, "fechaNacimiento").isEmpty());
        assertTrue(validator.validateProperty(pastUpdateRequest, "fechaNacimiento").isEmpty());
    }
}
