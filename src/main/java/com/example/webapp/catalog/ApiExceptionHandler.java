package com.example.webapp.catalog;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.SQLException;
import java.util.*;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
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
            case "JalaliDate" -> "تاریخ شمسی معتبر با قالب ۱۴۰۳/۰۱/۰۱ وارد کنید (سال ۰۰۰۱ تا ۱۹۹۹).";
            case "Size" -> "طول متن بیشتر از حد مجاز است.";
            case "Pattern" -> error.getField().equals("educationLevel") ? "مدرک تحصیلی را از گزینه‌های موجود انتخاب کنید." : "مقدار واردشده معتبر نیست.";
            default -> "مقدار واردشده معتبر نیست.";
        }));
        return ResponseEntity.badRequest().body(Map.of("message", "لطفاً فیلدهای مشخص‌شده را اصلاح کنید.", "fields", fields));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    ResponseEntity<?> duplicate(DuplicateKeyException e) {
        return ResponseEntity.status(409).body(Map.of("message", "این اطلاعات قبلاً ثبت شده است؛ کدها و ترکیب کارمند/شرکت/شغل، کارمند/عامل، شغل/عامل یا کارمند/دوره/تاریخ نباید تکراری باشند. برای تغییر، رکورد قبلی را ویرایش کنید."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<?> integrity(DataIntegrityViolationException e) {
        String sqlState = "";
        int vendorCode = 0;
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql) {
                sqlState = Objects.requireNonNullElse(sql.getSQLState(), "");
                vendorCode = sql.getErrorCode();
                break;
            }
        }
        // SQL exception messages can contain employee data; log diagnostic codes only.
        log.warn("Database write rejected: SQLState={}, vendorCode={}", sqlState, vendorCode);
        return switch (sqlState) {
            case "23503", "23506" -> ResponseEntity.status(409).body(Map.of("message",
                "این رکورد به اطلاعات دیگری وابسته است. ابتدا زیرمجموعه‌ها، انتساب‌ها یا سوابق مرتبط را بررسی کنید."));
            case "23513", "23514" -> ResponseEntity.badRequest().body(Map.of("message",
                "یکی از مقادیر واردشده با مقادیر مجاز بانک اطلاعات مطابقت ندارد. اگر گزینه را از فرم انتخاب کرده‌اید، برنامه باید با آخرین تغییرات بانک اجرا شود."));
            case "23502" -> ResponseEntity.badRequest().body(Map.of("message",
                "یکی از اطلاعات الزامی بانک خالی است. فیلدهای الزامی را تکمیل کنید."));
            case "22001" -> ResponseEntity.badRequest().body(Map.of("message",
                "طول یکی از متن‌های واردشده بیشتر از حد مجاز بانک اطلاعات است."));
            default -> ResponseEntity.internalServerError().body(Map.of("message",
                "ذخیره اطلاعات به دلیل ناسازگاری با بانک انجام نشد. خطای ثبت‌شده در خروجی برنامه را بررسی کنید."));
        };
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<?> malformed(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(Map.of("message", "قالب اطلاعات معتبر نیست. اعداد و تاریخ‌های ورودی را بررسی کنید."));
    }
}
