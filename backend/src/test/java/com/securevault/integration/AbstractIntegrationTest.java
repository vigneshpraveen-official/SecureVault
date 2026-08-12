package com.securevault.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared Testcontainers + MockMvc base for every P7.2 integration test. Containers are declared as
 * {@code static} fields on this ABSTRACT class specifically — Java gives every subclass the same
 * single copy of a superclass's static field, so all integration test classes share one running
 * Postgres and one running Redis for the whole suite instead of paying container-startup cost per
 * class.
 *
 * <p>Deliberately NOT annotated {@code @Container}: that annotation ties a container's start/stop
 * to ITS OWNING test class's JUnit lifecycle (started in that class's {@code @BeforeAll}, stopped
 * in its {@code @AfterAll}) — with a shared static field, the first integration test class to run
 * would stop the container out from under every class that runs after it, since the field is the
 * same instance but the extension's per-class bookkeeping doesn't know that (found live, running
 * the full suite: every class after the first failed register/login calls with 500s, while each
 * class passed in isolation). Testcontainers' documented "singleton container" pattern is instead:
 * start the container exactly once, manually, in a static initializer, and never stop it — the Ryuk
 * reaper container cleans it up when the whole JVM/test run exits. {@code @ServiceConnection} still
 * wires the connection details into Spring's context; it does not control lifecycle.
 *
 * <p>{@code disabledWithoutDocker = true} on {@link Testcontainers} is P7.2's explicit requirement:
 * if Docker is not available in the environment running the build, every test in every subclass is
 * SKIPPED, not failed — see docs/guide.md's "Running the test suite" section.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @ServiceConnection("redis")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;

    /**
     * Registers a fresh user and logs in, returning the access token for use as a Bearer header.
     */
    protected String registerAndLogin(String email, String password) throws Exception {
        String registerBody =
                objectMapper.writeValueAsString(
                        java.util.Map.of(
                                "fullName",
                                "Integration Test User",
                                "email",
                                email,
                                "password",
                                password));
        mockMvc.perform(
                        post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(registerBody))
                .andExpect(status().isCreated());

        String loginBody =
                objectMapper.writeValueAsString(
                        java.util.Map.of("email", email, "password", password));
        String loginResponse =
                mockMvc.perform(
                                post("/api/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(loginBody))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.accessToken").exists())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        return objectMapper.readTree(loginResponse).path("data").path("accessToken").asText();
    }
}
