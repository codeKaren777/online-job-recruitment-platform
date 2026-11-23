package com.groupcc2.recruitment.common.controller;

import com.groupcc2.recruitment.security.AccountJwtValidator;
import com.groupcc2.recruitment.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = RootController.class,
        properties = "app.security.jwt.secret-base64=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
@Import(SecurityConfig.class)
class RootControllerTest {
    @Autowired MockMvc mvc;

    @MockitoBean
    AccountJwtValidator accountJwtValidator;

    @Test
    void baseUrlReturnsPublicJsonResponse() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.service").value("online-job-recruitment-platform"))
                .andExpect(jsonPath("$.links.health").value("/actuator/health"));
    }
}
