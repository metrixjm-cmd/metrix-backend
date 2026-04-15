package com.metrix.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Constraint(validatedBy = RealBirthDateValidator.class)
@Target({FIELD})
@Retention(RUNTIME)
public @interface RealBirthDate {

    String message() default "La fecha de nacimiento debe ser real y no puede ser futura";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
