package com.metrix.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Constraint(validatedBy = OlderThanYearsValidator.class)
@Target({FIELD})
@Retention(RUNTIME)
public @interface OlderThanYears {

    String message() default "La fecha de nacimiento debe corresponder a una persona mayor de 17 años";

    int value();

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
