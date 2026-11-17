package com.example.webapp.catalog;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class EmployeeReferenceController {
    private final CatalogService service;
    public EmployeeReferenceController(CatalogService service) { this.service = service; }

    @PostMapping("/specialities") @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> createSpeciality(@Valid @RequestBody Requests.ReferenceItem r) {
        return Map.of("id", service.saveReference(Catalog.SPECIALITIES, null, r));
    }
    @PutMapping("/specialities/{id}")
    public Map<String, Long> updateSpeciality(@PathVariable long id, @Valid @RequestBody Requests.ReferenceItem r) {
        return Map.of("id", service.saveReference(Catalog.SPECIALITIES, id, r));
    }
    @PostMapping("/organizational-levels") @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> createLevel(@Valid @RequestBody Requests.ReferenceItem r) {
        return Map.of("id", service.saveReference(Catalog.ORGANIZATIONAL_LEVELS, null, r));
    }
    @PutMapping("/organizational-levels/{id}")
    public Map<String, Long> updateLevel(@PathVariable long id, @Valid @RequestBody Requests.ReferenceItem r) {
        return Map.of("id", service.saveReference(Catalog.ORGANIZATIONAL_LEVELS, id, r));
    }
}
