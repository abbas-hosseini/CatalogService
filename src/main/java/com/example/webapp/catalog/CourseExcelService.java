package com.example.webapp.catalog;

import jakarta.validation.Validator;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

@Service
public class CourseExcelService {
    private static final long MAX_BYTES = 5 * 1024 * 1024;
    private static final int MAX_ROWS = 1000;
    private static final Map<String, String> LABELS = Map.of("code", "کد دوره", "title", "عنوان دوره",
        "provider", "برگزارکننده", "durationHours", "مدت دوره (ساعت)", "description", "توضیحات");
    private final CatalogRepository repository;
    private final CatalogService catalog;
    private final Validator validator;

    public CourseExcelService(CatalogRepository repository, CatalogService catalog, Validator validator) {
        this.repository = repository; this.catalog = catalog; this.validator = validator;
        ZipSecureFile.setMaxEntrySize(16L * 1024 * 1024);
    }

    public record ImportRow(int rowNumber, Requests.Course course, String durationHours, List<String> errors) {}
    public record ImportReport(List<ImportRow> rows, int validCount, int errorCount, int importedCount, String message) {}

    @Transactional(readOnly = true)
    public ImportReport preview(MultipartFile file) { return inspect(file); }

    @Transactional
    public ImportReport importCourses(MultipartFile file) {
        // Revalidate at commit; a database conflict rolls back the entire batch.
        var report = inspect(file);
        if (report.errorCount() > 0) return report;
        for (var row : report.rows()) catalog.saveCourse(null, row.course());
        return new ImportReport(report.rows(), report.validCount(), 0, report.validCount(), "دوره‌های آموزشی با موفقیت ثبت شدند.");
    }

    private ImportReport inspect(MultipartFile file) {
        if (file.isEmpty()) throw new CatalogException(400, "فایل اکسل خالی است.");
        if (file.getSize() > MAX_BYTES) throw new CatalogException(413, "حجم فایل باید حداکثر ۵ مگابایت باشد.");
        String filename = Objects.requireNonNullElse(file.getOriginalFilename(), "").toLowerCase(Locale.ROOT);
        if (!filename.endsWith(".xlsx") && !filename.endsWith(".xls"))
            throw new CatalogException(400, "فایل اکسل با پسوند xlsx یا xls انتخاب کنید.");
        var rows = new ArrayList<ImportRow>();
        try (var input = file.getInputStream(); var workbook = WorkbookFactory.create(input)) {
            if (workbook.getNumberOfSheets() == 0) throw new CatalogException(400, "فایل برگه‌ای ندارد.");
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getLastRowNum() > MAX_ROWS)
                throw new CatalogException(400, "هر فایل می‌تواند حداکثر ۱۰۰۰ ردیف پس از عنوان ستون‌ها داشته باشد.");
            var formatter = new DataFormatter(Locale.ROOT);
            var columns = columns(sheet.getRow(0), formatter);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (blank(row, formatter)) continue;
                var errors = new ArrayList<String>();
                var values = new HashMap<String, String>();
                for (var column : columns.entrySet())
                    values.put(column.getValue(), value(row.getCell(column.getKey()), formatter, column.getValue(), errors));
                for (Cell cell : row)
                    if (!columns.containsKey(cell.getColumnIndex()) && !formatter.formatCellValue(cell).isBlank())
                        errors.add("برای ستون " + (cell.getColumnIndex() + 1) + " عنوان معتبر وارد کنید.");
                var course = new Requests.Course(values.get("code"), values.get("title"), values.get("provider"),
                    duration(values.get("durationHours"), errors), values.get("description"));
                validator.validate(course).stream().sorted(Comparator.comparing(v -> v.getPropertyPath().toString()))
                    .forEach(v -> errors.add(LABELS.get(v.getPropertyPath().toString()) + ": " +
                        switch (v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName()) {
                            case "Size" -> "طول متن بیشتر از حد مجاز است.";
                            case "DecimalMin", "DecimalMax" -> "مدت باید بیشتر از صفر و حداکثر ۱۰۰۰۰ ساعت باشد.";
                            case "Digits" -> "حداکثر دو رقم اعشار مجاز است.";
                            default -> "تکمیل این فیلد با مقدار معتبر الزامی است.";
                        }));
                rows.add(new ImportRow(i + 1, course, values.get("durationHours"), errors));
            }
        } catch (CatalogException e) {
            throw e;
        } catch (EncryptedDocumentException e) {
            throw new CatalogException(400, "فایل رمزگذاری شده است. یک نسخهٔ بدون رمز بارگذاری کنید.");
        } catch (IOException | RuntimeException e) {
            throw new CatalogException(400, "فایل اکسل قابل خواندن نیست؛ فایل سالم و بدون رمز را با قالب نمونه بارگذاری کنید.");
        }
        if (rows.isEmpty()) throw new CatalogException(400, "فایل هیچ ردیف اطلاعاتی ندارد. اطلاعات را از ردیف دوم برگهٔ اول وارد کنید.");
        var existing = new HashSet<String>();
        repository.list(Catalog.COURSES).forEach(course -> existing.add(codeKey(course.get("code").toString())));
        var counts = new HashMap<String, Integer>();
        for (var row : rows) if (row.course().code() != null) counts.merge(codeKey(row.course().code()), 1, Integer::sum);
        for (var row : rows) if (row.course().code() != null) {
            String code = codeKey(row.course().code());
            if (counts.get(code) > 1) row.errors().add("کد دوره در همین فایل تکرار شده است.");
            if (existing.contains(code)) row.errors().add("این کد دوره قبلاً در سامانه ثبت شده است؛ ورود اکسل فقط برای دوره‌های جدید است.");
        }
        int invalid = (int) rows.stream().filter(row -> !row.errors().isEmpty()).count();
        var immutableRows = rows.stream().map(row -> new ImportRow(row.rowNumber(), row.course(), row.durationHours(), List.copyOf(row.errors()))).toList();
        return new ImportReport(immutableRows, rows.size() - invalid, invalid, 0,
            invalid == 0 ? "اطلاعات آمادهٔ ثبت است. پیش‌نمایش را بررسی و ثبت را تأیید کنید." :
                "خطاهای فایل را اصلاح و دوباره بررسی کنید؛ هیچ ردیفی ذخیره نشده است.");
    }

    private static Map<Integer, String> columns(Row header, DataFormatter formatter) {
        if (header == null || header.getLastCellNum() > 10) throw new CatalogException(400, "عنوان ستون‌ها در ردیف اول و مطابق قالب نمونه باشد.");
        var columns = new LinkedHashMap<Integer, String>();
        for (Cell cell : header) {
            String title = formatter.formatCellValue(cell).strip();
            if (title.isBlank()) continue;
            if (cell.getCellType() != CellType.STRING) throw new CatalogException(400, "عنوان ستون باید متن ساده باشد.");
            String field = switch (title.replace('ي', 'ی').replace('ك', 'ک').replace("\u200c", "")
                .replace("\u00a0", "").replaceAll("\\s+", "").toLowerCase(Locale.ROOT)) {
                case "کددوره", "کد", "code", "coursecode" -> "code";
                case "عنواندوره", "نامدوره", "عنوان", "title" -> "title";
                case "برگزارکننده", "موسسهبرگزارکننده", "provider" -> "provider";
                case "مدتدوره(ساعت)", "مدت(ساعت)", "مدتدوره", "مدت", "durationhours" -> "durationHours";
                case "توضیحات", "description" -> "description";
                default -> throw new CatalogException(400, "عنوان ستون " + (cell.getColumnIndex() + 1) + " شناخته نشد. از قالب نمونه استفاده کنید.");
            };
            if (columns.containsValue(field)) throw new CatalogException(400, "ستون «" + LABELS.get(field) + "» بیش از یک بار آمده است.");
            columns.put(cell.getColumnIndex(), field);
        }
        if (!columns.values().containsAll(List.of("code", "title", "durationHours")))
            throw new CatalogException(400, "ستون‌های کد دوره، عنوان دوره و مدت دوره (ساعت) الزامی‌اند.");
        return columns;
    }

    private static boolean blank(Row row, DataFormatter formatter) {
        if (row == null) return true;
        for (Cell cell : row) if (!formatter.formatCellValue(cell).isBlank()) return false;
        return true;
    }

    private static String value(Cell cell, DataFormatter formatter, String field, List<String> errors) {
        if (cell == null || cell.getCellType() == CellType.BLANK) return null;
        if (cell.getCellType() == CellType.FORMULA || cell.getCellType() == CellType.ERROR || cell.getCellType() == CellType.BOOLEAN) {
            errors.add(LABELS.get(field) + ": مقدار مستقیم وارد کنید؛ فرمول، خطای اکسل و مقدار منطقی مجاز نیست.");
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell) || field.equals("durationHours") && cell.getCellStyle().getDataFormatString().contains("%")) {
                errors.add(LABELS.get(field) + ": مقدار عددی یا متن ساده وارد کنید؛ قالب تاریخ، ساعت و درصد مجاز نیست.");
                return null;
            }
            double numeric = cell.getNumericCellValue();
            if (field.equals("code") && (numeric != Math.rint(numeric) || Math.abs(numeric) > 999999999999999d)) {
                errors.add("کد دوره: کد را با قالب Text وارد کنید؛ اعشار و عدد بیش از ۱۵ رقم مجاز نیست.");
                return null;
            }
            // Read the stored number, not the rounded or scaled display format.
            if (field.equals("durationHours")) return BigDecimal.valueOf(numeric).stripTrailingZeros().toPlainString();
        }
        String result = formatter.formatCellValue(cell).strip();
        return result.isEmpty() ? null : result;
    }

    private static BigDecimal duration(String value, List<String> errors) {
        if (value == null) return null;
        var normalized = new StringBuilder();
        for (char c : value.toCharArray()) {
            if (c >= '۰' && c <= '۹') normalized.append((char) ('0' + c - '۰'));
            else if (c >= '٠' && c <= '٩') normalized.append((char) ('0' + c - '٠'));
            else normalized.append(c == '٫' ? '.' : c);
        }
        String text = normalized.toString();
        if (text.length() > 30 || !text.matches("-?[0-9]+(?:\\.[0-9]+)?")) {
            errors.add("مدت دوره (ساعت): عدد ساده با ممیز نقطه یا ٫ وارد کنید؛ مانند ۱۲٫۵. جداکنندهٔ هزارگان مجاز نیست.");
            return null;
        }
        return new BigDecimal(text).stripTrailingZeros();
    }

    private static String codeKey(String value) { return value.strip().toLowerCase(Locale.ROOT); }
}
