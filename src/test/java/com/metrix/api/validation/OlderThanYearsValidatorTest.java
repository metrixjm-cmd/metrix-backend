package com.metrix.api.validation;

import com.metrix.api.dto.CreateUserRequest;
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

class OlderThanYearsValidatorTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void create_request_rejects_exactly_twelve_years_old_today() {
        CreateUserRequest request = new CreateUserRequest();
        request.setFechaNacimiento(LocalDate.now().minusYears(12));

        Set<ConstraintViolation<CreateUserRequest>> violations = validator.validateProperty(request, "fechaNacimiento");

        assertFalse(violations.isEmpty());
    }

    @Test
    void create_request_rejects_younger_than_twelve_years() {
        CreateUserRequest request = new CreateUserRequest();
        request.setFechaNacimiento(LocalDate.now().minusYears(11));

        Set<ConstraintViolation<CreateUserRequest>> violations = validator.validateProperty(request, "fechaNacimiento");

        assertFalse(violations.isEmpty());
    }

    @Test
    void create_request_accepts_dates_older_than_twelve_years() {
        CreateUserRequest request = new CreateUserRequest();
        request.setFechaNacimiento(LocalDate.now().minusYears(12).minusDays(1));

        assertTrue(validator.validateProperty(request, "fechaNacimiento").isEmpty());
    }
}
