package com.example.webapp.catalog;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(CatalogException.class)
    ResponseEntity<?> domain(CatalogException e) { return ResponseEntity.status(e.status()).body(Map.of("message", e.getMessage())); }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> invalid(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(error -> fields.putIfAbsent(error.getField(), switch (Objects.requireNonNullElse(error.getCode(), "")) {
            case "NotNull", "NotBlank" -> "تکمیل این فیلد الزامی است.";
            case "DecimalMin", "DecimalMax" -> error.getField().equals("durationHours") ? "مدت دوره باید بیشتر از صفر و حداکثر ۱۰۰۰۰ ساعت باشد." : "نمره باید بین ۰ تا ۱۰۰ باشد.";
            case "Digits" -> "حداکثر دو رقم اعشار مجاز است.";
            case "PastOrPresent" -> "تاریخ نمی‌تواند در آینده باشد.";
            case "Size" -> "طول متن بیشتر از حد مجاز است.";
            default -> "مقدار واردشده معتبر نیست.";
        }));
        return ResponseEntity.badRequest().body(Map.of("message", "لطفاً فیلدهای مشخص‌شده را اصلاح کنید.", "fields", fields));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    ResponseEntity<?> duplicate(DuplicateKeyException e) {
        return ResponseEntity.status(409).body(Map.of("message", "این اطلاعات قبلاً ثبت شده است؛ کدها و ترکیب کارمند/عامل، شغل/عامل یا کارمند/دوره/تاریخ نباید تکراری باشند. برای تغییر، رکورد قبلی را ویرایش کنید."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<?> integrity(DataIntegrityViolationException e) {
        return ResponseEntity.status(409).body(Map.of("message", "این رکورد به اطلاعات دیگری وابسته است. ابتدا زیرمجموعه‌ها، انتساب‌ها یا سوابق مرتبط را بررسی کنید."));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<?> malformed(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(Map.of("message", "قالب اطلاعات معتبر نیست. اعداد و تاریخ‌های ورودی را بررسی کنید."));
    }
}
