package com.example.webapp.catalog;

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
public class AssessmentService {
    public static final int MAX_FILES = 10;
    public static final long MAX_FILE_BYTES = 5 * 1024 * 1024;
    private final JdbcTemplate jdbc;
    private final CatalogRepository repository;

    public AssessmentService(JdbcTemplate jdbc, CatalogRepository repository) {
        this.jdbc = jdbc;
        this.repository = repository;
    }

    public record Update(@Size(max = 5000) String strengths, @Size(max = 5000) String weaknesses,
                         @Size(max = 2000) String notes,
                         @Size(max = MAX_FILES) List<@NotNull @Positive Long> removedAttachmentIds) {}
    public record Attachment(long id, String fileName, long fileSize, LocalDateTime uploadedAt) {}
    public record Details(Map<String, Object> score, List<Attachment> attachments) {}
    public record Download(String fileName, byte[] content) {}

    @Transactional(readOnly = true)
    public Details details(long scoreId) {
        return new Details(repository.find(Catalog.SCORES, scoreId), attachments(scoreId));
    }

    public Details save(long scoreId, Update update, List<MultipartFile> files) {
        // Serialize attachment changes for this assessment, including concurrent uploads/deletion.
        if (jdbc.queryForList("SELECT id FROM employee_scores WHERE id = ? FOR UPDATE", Long.class, scoreId).isEmpty())
            throw new CatalogException(404, "سابقهٔ ارزیابی پیدا نشد.");
        var existing = attachments(scoreId);
        var removed = new HashSet<>(Objects.requireNonNullElse(update.removedAttachmentIds(), List.of()));
        var existingIds = new HashSet<>(existing.stream().map(Attachment::id).toList());
        if (!existingIds.containsAll(removed))
            throw new CatalogException(409, "یکی از پیوست‌ها متعلق به این ارزیابی نیست یا قبلاً حذف شده است. فرم را دوباره باز کنید.");
        if (existing.size() - removed.size() + files.size() > MAX_FILES)
            throw new CatalogException(400, "هر سابقهٔ ارزیابی می‌تواند حداکثر ۱۰ پیوست داشته باشد.");
        for (MultipartFile file : files) {
            if (file.isEmpty()) throw new CatalogException(400, "فایل خالی قابل پیوست نیست.");
            if (file.getSize() > MAX_FILE_BYTES) throw new CatalogException(413, "حجم هر پیوست باید حداکثر ۵ مگابایت باشد.");
            filename(file);
        }
        jdbc.update("UPDATE employee_scores SET strengths = ?, weaknesses = ?, notes = ? WHERE id = ?",
            clean(update.strengths()), clean(update.weaknesses()), clean(update.notes()), scoreId);
        for (long id : removed) jdbc.update("DELETE FROM assessment_attachments WHERE score_id = ? AND id = ?", scoreId, id);
        for (MultipartFile file : files) {
            try {
                // Read a bounded stream, independent of the size reported by the client.
                byte[] content;
                try (var input = file.getInputStream()) { content = input.readNBytes((int) MAX_FILE_BYTES + 1); }
                if (content.length == 0) throw new CatalogException(400, "فایل خالی قابل پیوست نیست.");
                if (content.length > MAX_FILE_BYTES) throw new CatalogException(413, "حجم هر پیوست باید حداکثر ۵ مگابایت باشد.");
                jdbc.update("INSERT INTO assessment_attachments(score_id, file_name, file_size, content) VALUES (?, ?, ?, ?)",
                    scoreId, filename(file), content.length, content);
            } catch (IOException e) {
                // Runtime exception rolls back text, removals and all preceding uploads together.
                throw new CatalogException(400, "خواندن پیوست انجام نشد؛ هیچ‌یک از تغییرات ذخیره نشد. دوباره تلاش کنید.");
            }
        }
        return details(scoreId);
    }

    @Transactional(readOnly = true)
    public Download download(long scoreId, long attachmentId) {
        var files = jdbc.query("SELECT file_name, content FROM assessment_attachments WHERE score_id = ? AND id = ?",
            (rs, n) -> new Download(rs.getString("file_name"), rs.getBytes("content")), scoreId, attachmentId);
        if (files.isEmpty()) throw new CatalogException(404, "پیوست موردنظر پیدا نشد.");
        return files.getFirst();
    }

    private List<Attachment> attachments(long scoreId) {
        return jdbc.query("SELECT id, file_name, file_size, uploaded_at FROM assessment_attachments WHERE score_id = ? ORDER BY id",
            (rs, n) -> new Attachment(rs.getLong("id"), rs.getString("file_name"), rs.getLong("file_size"),
                rs.getTimestamp("uploaded_at").toLocalDateTime()), scoreId);
    }

    private static String filename(MultipartFile file) {
        String name = Objects.requireNonNullElse(file.getOriginalFilename(), "").replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\\p{Cf}]", "").strip();
        if (name.isBlank() || name.equals(".") || name.equals("..") || name.length() > 255)
            throw new CatalogException(400, "نام پیوست باید معتبر و حداکثر ۲۵۵ کاراکتر باشد.");
        return name;
    }

    private static String clean(String text) { return text == null || text.isBlank() ? null : text.strip(); }
}
