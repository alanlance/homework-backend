package com.example.demo.api;

import com.example.demo.service.RateLimitService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping
public class LimitController {

    private final RateLimitService service;

    public LimitController(RateLimitService service) {
        this.service = service;
    }

    @PostMapping("/limits")
    public ResponseEntity<LimitResponse> upsert(@Valid @RequestBody LimitRequest request) {
        var result = service.upsertRule(request.apiKey(), request.limit(), request.windowSeconds());
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(LimitResponse.from(result.rule()));
    }

    @GetMapping("/check")
    public ResponseEntity<CheckResponse> check(
            @RequestParam @NotBlank @Size(max = 128) String apiKey) {
        var result = service.check(apiKey);
        var response = new CheckResponse(
                result.apiKey(), result.allowed(), result.usage(), result.remaining(), result.ttl());
        return result.allowed()
                ? ResponseEntity.ok(response)
                : ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(response);
    }

    @GetMapping("/usage")
    public UsageResponse usage(@RequestParam @NotBlank @Size(max = 128) String apiKey) {
        var result = service.usage(apiKey);
        return new UsageResponse(result.apiKey(), result.usage(), result.remaining(), result.ttl());
    }

    @GetMapping("/limits")
    public LimitsPageResponse list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        var result = service.listRules(page, size);
        List<LimitResponse> content = result.content().stream().map(LimitResponse::from).toList();
        return new LimitsPageResponse(
                content, result.page(), result.size(), result.totalElements(), result.totalPages());
    }

    @DeleteMapping("/limits/{apiKey}")
    public ResponseEntity<Void> delete(@PathVariable @NotBlank @Size(max = 128) String apiKey) {
        service.deleteRule(apiKey);
        return ResponseEntity.noContent().build();
    }

    public record CheckResponse(String apiKey, boolean allowed, long usage, long remaining, long ttl) {
    }

    public record UsageResponse(String apiKey, long usage, long remaining, long ttl) {
    }

    public record LimitsPageResponse(
            List<LimitResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }
}
