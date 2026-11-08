package com.example.webapp.dates;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class JalaliDateValidator implements ConstraintValidator<JalaliDate, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return JalaliDates.isValid(value);
    }
}
