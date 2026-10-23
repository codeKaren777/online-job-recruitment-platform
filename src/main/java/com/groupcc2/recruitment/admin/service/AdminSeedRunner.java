package com.groupcc2.recruitment.admin.service;

import com.groupcc2.recruitment.users.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.admin-seed.enabled", havingValue = "true")
public class AdminSeedRunner implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminSeedRunner.class);

    private final UserService userService;
    private final ConfigurableApplicationContext applicationContext;
    private final String email;
    private final String password;

    public AdminSeedRunner(
            UserService userService,
            ConfigurableApplicationContext applicationContext,
            @Value("${app.admin-seed.email:}") String email,
            @Value("${app.admin-seed.password:}") String password) {
        this.userService = userService;
        this.applicationContext = applicationContext;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(String... args) {
        if (email.isBlank() || password.isBlank()) {
            throw new IllegalArgumentException(
                    "ADMIN_EMAIL and ADMIN_PASSWORD are required when admin seeding is enabled");
        }
        userService.seedAdministrator(email, password);
        log.info("Administrator seed completed");
        SpringApplication.exit(applicationContext, () -> 0);
    }
}
