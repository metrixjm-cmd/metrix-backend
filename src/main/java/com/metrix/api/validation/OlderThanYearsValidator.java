package com.metrix.api.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDate;

public class OlderThanYearsValidator implements ConstraintValidator<OlderThanYears, LocalDate> {

    private int years;

    @Override
    public void initialize(OlderThanYears constraintAnnotation) {
        this.years = constraintAnnotation.value();
    }

    @Override
    public boolean isValid(LocalDate value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        LocalDate cutoff = LocalDate.now().minusYears(years);
        return value.isBefore(cutoff);
    }
}
