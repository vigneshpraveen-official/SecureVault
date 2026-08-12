package com.securevault.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * P7.2 journey 2: pagination + sorting + filtering, combined, against real seeded data — the S4.5
 * dynamic-filter API (page/size/sortBy/direction/category/title/username/website, all optional and
 * freely combinable) exercised through Specification composition against a real Postgres container
 * rather than mocked.
 */
class VaultPaginationIntegrationTest extends AbstractIntegrationTest {

    private String accessToken;

    @BeforeEach
    void seedFifteenCredentials() throws Exception {
        // A fresh, unique email every run: the shared singleton container (AbstractIntegrationTest)
        // deliberately persists data across every @Test method in this class (no per-method
        // transaction rollback), so a fixed literal email here would 409 on the 2nd method onward
        // — found live running the full suite, not a hypothetical.
        String email = "pagination-" + java.util.UUID.randomUUID() + "@example.com";
        accessToken = registerAndLogin(email, "Str0ng!Pass1");

        List<String> categories = List.of("WORK", "DEVELOPMENT", "PERSONAL");
        for (int i = 0; i < 15; i++) {
            String category = categories.get(i % categories.size());
            String title = String.format("Site-%02d", i);
            String body =
                    objectMapper.writeValueAsString(
                            Map.of(
                                    "title",
                                    title,
                                    "username",
                                    "user" + i,
                                    "password",
                                    "Pass" + i + "word1!",
                                    "category",
                                    category));
            mockMvc.perform(
                            post("/api/vault")
                                    .header("Authorization", "Bearer " + accessToken)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                    .andExpect(status().isCreated());
        }
    }

    @Test
    void should_paginateWithCorrectTotalsAndPageCount() throws Exception {
        mockMvc.perform(
                        get("/api/vault")
                                .header("Authorization", "Bearer " + accessToken)
                                .param("page", "0")
                                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(10))
                .andExpect(jsonPath("$.data.totalElements").value(15))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.first").value(true))
                .andExpect(jsonPath("$.data.last").value(false));

        mockMvc.perform(
                        get("/api/vault")
                                .header("Authorization", "Bearer " + accessToken)
                                .param("page", "1")
                                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(5))
                .andExpect(jsonPath("$.data.last").value(true));
    }

    @Test
    void should_returnAnEmptyPageBeyondTheLastPage_notAnError() throws Exception {
        mockMvc.perform(
                        get("/api/vault")
                                .header("Authorization", "Bearer " + accessToken)
                                .param("page", "5")
                                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(15));
    }

    @Test
    void should_sortAscendingAndDescendingByTitle() throws Exception {
        mockMvc.perform(
                        get("/api/vault")
                                .header("Authorization", "Bearer " + accessToken)
                                .param("size", "100")
                                .param("sortBy", "title")
                                .param("direction", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].title").value("Site-00"))
                .andExpect(jsonPath("$.data.content[14].title").value("Site-14"));

        mockMvc.perform(
                        get("/api/vault")
                                .header("Authorization", "Bearer " + accessToken)
                                .param("size", "100")
                                .param("sortBy", "title")
                                .param("direction", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].title").value("Site-14"))
                .andExpect(jsonPath("$.data.content[14].title").value("Site-00"));
    }

    @Test
    void should_rejectAnUnwhitelistedSortField_with400NotA500() throws Exception {
        mockMvc.perform(
                        get("/api/vault")
                                .header("Authorization", "Bearer " + accessToken)
                                .param("sortBy", "encryptedPassword"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_filterByCategoryAlone() throws Exception {
        mockMvc.perform(
                        get("/api/vault")
                                .header("Authorization", "Bearer " + accessToken)
                                .param("size", "100")
                                .param("category", "PERSONAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(5));
    }

    @Test
    void should_filterByTitleAlone() throws Exception {
        mockMvc.perform(
                        get("/api/vault")
                                .header("Authorization", "Bearer " + accessToken)
                                .param("size", "100")
                                .param("title", "Site-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void should_combineCategoryAndTitleFiltersWithSortingAndPagination() throws Exception {
        mockMvc.perform(
                        get("/api/vault")
                                .header("Authorization", "Bearer " + accessToken)
                                .param("page", "0")
                                .param("size", "5")
                                .param("sortBy", "title")
                                .param("direction", "asc")
                                .param("category", "WORK")
                                .param("title", "Site"))
                .andExpect(status().isOk())
                // 15 credentials, 1 in 3 is WORK -> Site-00, 03, 06, 09, 12 = 5.
                .andExpect(jsonPath("$.data.totalElements").value(5))
                .andExpect(jsonPath("$.data.content[0].title").value("Site-00"));
    }
}
