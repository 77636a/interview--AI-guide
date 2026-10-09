package interview.guide.modules.resume;

import interview.guide.common.exception.ErrorCodes;
import interview.guide.common.result.Result;
import interview.guide.infrastructure.persistence.DatabaseHealthService;
import interview.guide.infrastructure.persistence.DatabaseStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/resumes")
@Tag(name = "Resume", description = "Resume module entry points")
public class ResumeHealthController {

    private final DatabaseHealthService databaseHealthService;

    public ResumeHealthController(DatabaseHealthService databaseHealthService) {
        this.databaseHealthService = databaseHealthService;
    }

    @GetMapping("/health")
    @Operation(summary = "Check application and database status")
    @ApiResponse(responseCode = "200", description = "Application and database are available")
    @ApiResponse(responseCode = "503", description = "Database is unavailable")
    public ResponseEntity<Result<HealthResponse>> health(
            @RequestParam(defaultValue = "basic")
            @Pattern(regexp = "basic|full", message = "detail must be basic or full") String detail) {
        DatabaseStatus databaseStatus = databaseHealthService.check();
        HealthResponse response = new HealthResponse(
                "UP",
                databaseStatus.available() ? "UP" : "DOWN",
                Instant.now(),
                MDC.get("traceId"));

        if (!databaseStatus.available()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Result.failure(ErrorCodes.BASE_DATABASE_UNAVAILABLE, "database unavailable"));
        }
        return ResponseEntity.ok(Result.success(response));
    }

    public record HealthResponse(String application, String database, Instant timestamp, String traceId) {
    }
}

