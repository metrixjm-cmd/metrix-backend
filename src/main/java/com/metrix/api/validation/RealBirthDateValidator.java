package com.metrix.api.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class RealBirthDateValidator implements ConstraintValidator<RealBirthDate, LocalDate> {

    private static final long MAX_HUMAN_AGE_DAYS = Math.round(120 * 365.25);

    @Override
    public boolean isValid(LocalDate value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        LocalDate today = LocalDate.now();
        if (value.isAfter(today)) {
            return false;
        }

        long ageInDays = ChronoUnit.DAYS.between(value, today);
        return ageInDays <= MAX_HUMAN_AGE_DAYS;
    }
}
