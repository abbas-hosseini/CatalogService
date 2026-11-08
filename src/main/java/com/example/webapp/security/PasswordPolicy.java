package com.example.webapp.security;

import com.example.webapp.catalog.CatalogException;
import java.util.Locale;

public final class PasswordPolicy {
    private PasswordPolicy() {}
    public static final String MESSAGE = "رمز باید ۱۵ تا ۱۲۸ نویسه و شامل حرف بزرگ و کوچک انگلیسی، عدد و نشانه باشد؛ نام کاربری و رمزهای رایج مجاز نیستند.";

    public static void validate(String password, String username) {
        if (password == null || password.length() < 15 || password.length() > 128 ||
            !password.matches("(?s).*[A-Z].*") || !password.matches("(?s).*[a-z].*") ||
            !password.matches("(?s).*[0-9].*") || password.codePoints().noneMatch(c -> !Character.isLetterOrDigit(c) && !Character.isWhitespace(c)) ||
            password.codePoints().anyMatch(Character::isISOControl)) throw new CatalogException(400, MESSAGE);
        String lower = password.toLowerCase(Locale.ROOT);
        if ((username.length() >= 3 && lower.contains(username.toLowerCase(Locale.ROOT))) ||
            lower.contains("password") || lower.contains("qwerty") || lower.contains("123456") || lower.contains("letmein"))
            throw new CatalogException(400, MESSAGE);
    }
}
