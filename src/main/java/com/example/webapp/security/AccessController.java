package com.example.webapp.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
public class AccessController {
    private final AccessControl access;
    public AccessController(AccessControl access) { this.access = access; }

    @GetMapping("/api/access/me") public AccessControl.MyAccess mine() { return access.mine(); }
    @GetMapping("/api/admin/permissions") public List<Permissions.Group> permissions() { return Permissions.GROUPS; }
    @GetMapping("/api/admin/roles") public List<AccessControl.Role> roles() { return access.roles(); }
    @GetMapping("/api/admin/users") public List<AccessControl.User> users() { return access.users(); }

    public record RoleRequest(@NotBlank @Size(max = 80) String name, @Size(max = 1000) String description,
                              @NotNull @Size(max = 64) Set<@NotBlank String> permissions) {}
    public record NewUser(@NotBlank @Size(max = 64) String username, @NotBlank @Size(max = 120) String displayName,
                          @Email @Size(max = 254) String email, @NotBlank @Size(max = 128) String password,
                          @NotBlank @Size(max = 128) String confirmation, @NotNull Boolean enabled,
                          @NotNull @Size(max = 100) Set<@NotNull @Positive Long> roleIds) {}
    public record UpdateUser(@NotBlank @Size(max = 120) String displayName, @Email @Size(max = 254) String email,
                             @NotNull Boolean enabled, @NotNull @Size(max = 100) Set<@NotNull @Positive Long> roleIds) {}

    @PostMapping("/api/admin/roles") @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> createRole(@Valid @RequestBody RoleRequest r) { return Map.of("id", access.saveRole(null, r.name(), r.description(), r.permissions())); }
    @PutMapping("/api/admin/roles/{id}")
    public Map<String, Long> updateRole(@PathVariable long id, @Valid @RequestBody RoleRequest r) { return Map.of("id", access.saveRole(id, r.name(), r.description(), r.permissions())); }
    @DeleteMapping("/api/admin/roles/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRole(@PathVariable long id) { access.deleteRole(id); }
    @PostMapping("/api/admin/users") @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> createUser(@Valid @RequestBody NewUser r) {
        return Map.of("id", access.createUser(r.username(), r.displayName(), r.email(), r.password(), r.confirmation(), r.enabled(), r.roleIds()));
    }
    @PutMapping("/api/admin/users/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateUser(@PathVariable long id, @Valid @RequestBody UpdateUser r) { access.updateUser(id, r.displayName(), r.email(), r.enabled(), r.roleIds()); }
}
