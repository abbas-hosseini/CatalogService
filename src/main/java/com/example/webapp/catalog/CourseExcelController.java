package com.example.webapp.catalog;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/courses/import")
public class CourseExcelController {
    private final CourseExcelService service;
    public CourseExcelController(CourseExcelService service) { this.service = service; }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CourseExcelService.ImportReport preview(@RequestParam("file") MultipartFile file) {
        return service.preview(file);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CourseExcelService.ImportReport> importCourses(@RequestParam("file") MultipartFile file) {
        var report = service.importCourses(file);
        return ResponseEntity.status(report.errorCount() > 0 ? 422 : 201).body(report);
    }
}
