package com.qfion.challenge.controller;

import com.qfion.challenge.service.DailyLimitResetService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/daily-limit")
@RequiredArgsConstructor
public class AdminResetController {
    private final DailyLimitResetService service;

    public record ResetRequest(
            @NotEmpty @Size(max = 100) List<@NotBlank @Email @Size(max = 255) String> emails,
            @NotNull @AssertTrue Boolean confirmed) {}

    @PostMapping("/reset")
    public DailyLimitResetService.ResetResult reset(@Valid @RequestBody ResetRequest request,
            @RequestAttribute("authenticatedAdmin") String adminEmail) {
        return service.reset(request.emails(), adminEmail);
    }

    @ExceptionHandler(CannotAcquireLockException.class)
    public ResponseEntity<?> busy() {
        return ResponseEntity.status(409).body(Map.of("error",
                "A submission is still processing. No reset was applied. Try again shortly."));
    }
}
