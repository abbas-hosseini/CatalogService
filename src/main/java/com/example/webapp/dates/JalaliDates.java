package com.example.webapp.dates;

import com.github.mfathi91.time.PersianDate;
import java.time.DateTimeException;
import java.util.Locale;

/** Canonical storage/API format: Jalali yyyy-MM-dd with ASCII digits. */
public final class JalaliDates {
    private JalaliDates() {}

    public static String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        StringBuilder normalized = new StringBuilder();
        for (char c : value.strip().toCharArray()) {
            if (c >= '۰' && c <= '۹') normalized.append((char) ('0' + c - '۰'));
            else if (c >= '٠' && c <= '٩') normalized.append((char) ('0' + c - '٠'));
            else normalized.append(c == '/' ? '-' : c);
        }
        String result = normalized.toString();
        if (!result.matches("[0-9]{4}-[0-9]{1,2}-[0-9]{1,2}")) return result;
        String[] parts = result.split("-");
        return String.format(Locale.ROOT, "%04d-%02d-%02d", Integer.parseInt(parts[0]),
            Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }

    public static boolean isValid(String value) {
        if (value == null) return true;
        if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) return false;
        try {
            int year = Integer.parseInt(value.substring(0, 4));
            int month = Integer.parseInt(value.substring(5, 7));
            int day = Integer.parseInt(value.substring(8, 10));
            if (year < 1 || year > 1999 || month < 1 || month > 12 || day < 1 || day > 31) return false;
            // Check month length and leap years after explicitly guarding the lower bounds.
            PersianDate.of(year, month, day);
            return true;
        } catch (DateTimeException | IllegalArgumentException e) {
            return false;
        }
    }
}
