package com.example.webapp.catalog;

import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api/trainings")
public class TrainingRecordController {
    private final TrainingRecordService service;
    public TrainingRecordController(TrainingRecordService service) { this.service = service; }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> create(@Valid @RequestPart("details") TrainingRecordService.Submission submission,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        return Map.of("id", service.save(null, submission, files == null ? List.of() : files));
    }
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Long> update(@PathVariable long id, @Valid @RequestPart("details") TrainingRecordService.Submission submission,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        return Map.of("id", service.save(id, submission, files == null ? List.of() : files));
    }
    @GetMapping("/{id}/attachments")
    public List<TrainingRecordService.Attachment> attachments(@PathVariable long id) { return service.attachments(id); }

    @GetMapping("/{id}/attachments/{attachmentId}")
    public ResponseEntity<byte[]> download(@PathVariable long id, @PathVariable long attachmentId) {
        var file = service.download(id, attachmentId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
            .header("X-Content-Type-Options", "nosniff").cacheControl(CacheControl.noStore())
            .contentLength(file.content().length).body(file.content());
    }
}
