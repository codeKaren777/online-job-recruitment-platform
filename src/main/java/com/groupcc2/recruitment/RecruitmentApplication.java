package com.groupcc2.recruitment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

@SpringBootApplication
public class RecruitmentApplication {
    private static final Logger log = LoggerFactory.getLogger(RecruitmentApplication.class);

    @Value("${server.port:8080}")
    private int serverPort;

    public static void main(String[] args) {
        SpringApplication.run(RecruitmentApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    void reportReady() {
        log.info("Recruitment backend is ready and listening at http://localhost:{}", serverPort);
        log.info("Backend health check: http://localhost:{}/actuator/health", serverPort);
    }
}
