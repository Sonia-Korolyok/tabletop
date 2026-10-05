package com.tabletop.recipe;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Recipe CRUD through HTTP, against a real PostgreSQL (needs Docker). */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class RecipeApiTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    MockMvc mvc;

    String token;

    static final String LASAGNA = """
            {
              "title": "Классическая мясная лазанья",
              "servings": 4,
              "totalTimeMin": 90,
              "sourceUrl": "https://example.com/lasagna",
              "ingredients": ["500 г фарша", "1 1/2 ч. л. соли", "2 яйца", "соль по вкусу"],
              "steps": ["Обжарить фарш", "Собрать слои", "Запечь 40 минут"],
              "tags": ["Italian", "italian ", "Dinner"]
            }""";

    @BeforeEach
    void registerUser() throws Exception {
        token = registerAndGetToken();
    }

    @Test
    void createReturns201WithLocationAndParsedIngredients() throws Exception {
        mvc.perform(auth(post("/api/recipes"), token).content(LASAGNA))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern(".*/api/recipes/\\d+")))
                .andExpect(jsonPath("$.title").value("Классическая мясная лазанья"))
                .andExpect(jsonPath("$.sourceType").value("MANUAL"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.ingredients[0].quantity").value(500))
                .andExpect(jsonPath("$.ingredients[0].unit").value("g"))
                .andExpect(jsonPath("$.ingredients[0].name").value("фарша"))
                .andExpect(jsonPath("$.ingredients[3].quantity").doesNotExist())
                .andExpect(jsonPath("$.steps[2].position").value(2))
                .andExpect(jsonPath("$.tags", contains("Dinner", "Italian")));  // duplicates merged
    }

    @Test
    void getWithServingsScalesQuantities() throws Exception {
        long id = create(LASAGNA);

        mvc.perform(auth(get("/api/recipes/" + id + "?servings=2"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servings").value(4))
                .andExpect(jsonPath("$.requestedServings").value(2))
                .andExpect(jsonPath("$.ingredients[0].scaled.display").value("250"))
                .andExpect(jsonPath("$.ingredients[1].scaled.display").value("¾"))
                .andExpect(jsonPath("$.ingredients[2].scaled.display").value("1"))
                .andExpect(jsonPath("$.ingredients[3].scaled").doesNotExist());
    }

    @Test
    void updateReplacesContentAndBumpsVersion() throws Exception {
        long id = create(LASAGNA);
        String edited = """
                {"title": "Лазанья с бешамелем", "servings": 6,
                 "ingredients": ["700 г фарша", "1 л молока"], "steps": ["Сварить бешамель"],
                 "tags": ["Italian"], "version": 0}""";

        mvc.perform(auth(put("/api/recipes/" + id), token).content(edited))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Лазанья с бешамелем"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.ingredients", hasSize(2)))
                .andExpect(jsonPath("$.steps", hasSize(1)))
                .andExpect(jsonPath("$.tags", contains("Italian")));

        // second device still has version 0
        mvc.perform(auth(put("/api/recipes/" + id), token).content(edited))
                .andExpect(status().isConflict());
    }

    @Test
    void favoriteDoesNotChangeVersion() throws Exception {
        long id = create(LASAGNA);

        mvc.perform(auth(patch("/api/recipes/" + id + "/favorite"), token).content("{\"favorite\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favorite").value(true))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void deleteHidesRecipeAndRestoreBringsItBack() throws Exception {
        long id = create(LASAGNA);

        mvc.perform(auth(delete("/api/recipes/" + id), token)).andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/recipes/" + id), token)).andExpect(status().isNotFound());

        mvc.perform(auth(post("/api/recipes/" + id + "/restore"), token)).andExpect(status().isOk());
        mvc.perform(auth(get("/api/recipes/" + id), token)).andExpect(status().isOk());
    }

    @Test
    void someoneElsesRecipeIs404ForEveryOperation() throws Exception {
        long id = create(LASAGNA);
        String stranger = registerAndGetToken();

        mvc.perform(auth(get("/api/recipes/" + id), stranger)).andExpect(status().isNotFound());
        mvc.perform(auth(put("/api/recipes/" + id), stranger).content(LASAGNA.replace("}", ",\"version\":0}")))
                .andExpect(status().isNotFound());
        mvc.perform(auth(delete("/api/recipes/" + id), stranger)).andExpect(status().isNotFound());
        mvc.perform(auth(post("/api/recipes/" + id + "/restore"), stranger)).andExpect(status().isNotFound());
    }

    @Test
    void invalidRecipeIs400WithFieldErrors() throws Exception {
        mvc.perform(auth(post("/api/recipes"), token)
                        .content("{\"title\": \"  \", \"servings\": 0, \"sourceUrl\": \"ftp://x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists())
                .andExpect(jsonPath("$.errors.servings").exists())
                .andExpect(jsonPath("$.errors.sourceUrl").exists());
    }

    @Test
    void withoutTokenIs401() throws Exception {
        mvc.perform(get("/api/recipes/1")).andExpect(status().isUnauthorized());
    }

    // --- helpers ---

    private long create(String body) throws Exception {
        String json = mvc.perform(auth(post("/api/recipes"), token).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private String registerAndGetToken() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        String json = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.accessToken");
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder req, String token) {
        return req.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }
}
