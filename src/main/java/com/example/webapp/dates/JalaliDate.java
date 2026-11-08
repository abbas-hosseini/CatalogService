package com.example.webapp.dates;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = JalaliDateValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface JalaliDate {
    String message() default "تاریخ شمسی معتبر وارد کنید.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
