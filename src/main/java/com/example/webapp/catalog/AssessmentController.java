package com.example.webapp.catalog;

import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/scores/{scoreId}")
public class AssessmentController {
    private final AssessmentService service;
    public AssessmentController(AssessmentService service) { this.service = service; }

    @GetMapping("/details")
    public AssessmentService.Details details(@PathVariable long scoreId) { return service.details(scoreId); }

    @PutMapping(value = "/details", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AssessmentService.Details save(@PathVariable long scoreId,
            @Valid @RequestPart("details") AssessmentService.Update details,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        return service.save(scoreId, details, files == null ? List.of() : files);
    }

    @GetMapping("/attachments/{attachmentId}")
    public ResponseEntity<byte[]> download(@PathVariable long scoreId, @PathVariable long attachmentId) {
        var file = service.download(scoreId, attachmentId);
        // All file types are downloaded as opaque bytes; uploaded HTML/SVG never renders on our origin.
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
            .header("X-Content-Type-Options", "nosniff")
            .cacheControl(CacheControl.noStore())
            .contentLength(file.content().length).body(file.content());
    }
}
