package com.groupcc2.recruitment.common.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RootController {
    @GetMapping("/")
    public Map<String, Object> root() {
        return Map.of(
                "status", "ok",
                "service", "online-job-recruitment-platform",
                "links", Map.of(
                        "health", "/actuator/health",
                        "apiDocs", "/swagger-ui/index.html"));
    }
}
