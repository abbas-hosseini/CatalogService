package com.example.webapp.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Transactional
public class TrainingRecordService {
    public static final int MAX_FILES = 10;
    public static final long MAX_FILE_BYTES = 5 * 1024 * 1024;
    private final JdbcTemplate jdbc;
    private final CatalogService catalog;
    private final CatalogRepository repository;

    public TrainingRecordService(JdbcTemplate jdbc, CatalogService catalog, CatalogRepository repository) {
        this.jdbc = jdbc;this.catalog = catalog;this.repository = repository;
    }
    public record Submission(@NotNull @Valid Requests.Training training,
                             @Size(max = MAX_FILES) List<@NotNull @Positive Long> removedAttachmentIds) {}
    public record Attachment(long id, String fileName, long fileSize, LocalDateTime uploadedAt) {}
    public record Download(String fileName, byte[] content) {}

    @Transactional(readOnly = true)
    public List<Attachment> attachments(long trainingId) {
        if (!repository.exists(Catalog.TRAININGS, trainingId)) throw new CatalogException(404, "سابقهٔ آموزشی پیدا نشد.");
        return jdbc.query("SELECT id, file_name, file_size, uploaded_at FROM training_attachments WHERE training_id = ? ORDER BY id",
            (rs, n) -> new Attachment(rs.getLong("id"), rs.getString("file_name"), rs.getLong("file_size"),
                rs.getTimestamp("uploaded_at").toLocalDateTime()), trainingId);
    }

    public long save(Long trainingId, Submission submission, List<MultipartFile> files) {
        // Lock the parent before checking limits; concurrent uploads cannot exceed the limit.
        if (trainingId != null && jdbc.queryForList("SELECT id FROM employee_trainings WHERE id = ? FOR UPDATE", Long.class, trainingId).isEmpty())
            throw new CatalogException(404, "سابقهٔ آموزشی پیدا نشد.");
        var existing = trainingId == null ? List.<Attachment>of() : attachments(trainingId);
        var removed = new HashSet<>(Objects.requireNonNullElse(submission.removedAttachmentIds(), List.of()));
        var existingIds = new HashSet<>(existing.stream().map(Attachment::id).toList());
        if (!existingIds.containsAll(removed))
            throw new CatalogException(409, "یکی از پیوست‌ها متعلق به این سابقه نیست یا قبلاً حذف شده است. فرم را دوباره باز کنید.");
        if (existing.size() - removed.size() + files.size() > MAX_FILES)
            throw new CatalogException(400, "هر سابقهٔ آموزشی می‌تواند حداکثر ۱۰ پیوست داشته باشد.");
        for (var file : files) {
            if (file.isEmpty()) throw new CatalogException(400, "فایل خالی قابل پیوست نیست.");
            if (file.getSize() > MAX_FILE_BYTES) throw new CatalogException(413, "حجم هر پیوست باید حداکثر ۵ مگابایت باشد.");
            filename(file);
        }
        long savedId = catalog.saveTraining(trainingId, submission.training());
        for (long id : removed) jdbc.update("DELETE FROM training_attachments WHERE training_id = ? AND id = ?", savedId, id);
        for (var file : files) {
            try {
                byte[] content;
                try (var input = file.getInputStream()) { content = input.readNBytes((int) MAX_FILE_BYTES + 1); }
                if (content.length == 0) throw new CatalogException(400, "فایل خالی قابل پیوست نیست.");
                if (content.length > MAX_FILE_BYTES) throw new CatalogException(413, "حجم هر پیوست باید حداکثر ۵ مگابایت باشد.");
                jdbc.update("INSERT INTO training_attachments(training_id, file_name, file_size, content) VALUES (?, ?, ?, ?)",
                    savedId, filename(file), content.length, content);
            } catch (IOException e) {
                // Roll back the training, all uploaded files and all removals together.
                throw new CatalogException(400, "خواندن پیوست انجام نشد؛ هیچ‌یک از تغییرات ذخیره نشد. دوباره تلاش کنید.");
            }
        }
        return savedId;
    }

    @Transactional(readOnly = true)
    public Download download(long trainingId, long attachmentId) {
        var files = jdbc.query("SELECT file_name, content FROM training_attachments WHERE training_id = ? AND id = ?",
            (rs, n) -> new Download(rs.getString("file_name"), rs.getBytes("content")), trainingId, attachmentId);
        if (files.isEmpty()) throw new CatalogException(404, "پیوست موردنظر پیدا نشد.");
        return files.getFirst();
    }

    private static String filename(MultipartFile file) {
        String name = Objects.requireNonNullElse(file.getOriginalFilename(), "").replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\\p{Cf}]", "").strip();
        if (name.isBlank() || name.equals(".") || name.equals("..") || name.length() > 255)
            throw new CatalogException(400, "نام پیوست باید معتبر و حداکثر ۲۵۵ کاراکتر باشد.");
        return name;
    }
}
