package com.cryptocinema;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.cryptocinema.repository.MovieRepository;
import com.cryptocinema.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:movie-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.jwt.secret=12345678901234567890123456789012",
        "app.jwt.expiration-ms=3600000",
        "app.admin.email=",
        "app.admin.password="
})
@AutoConfigureMockMvc
class MovieIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MovieRepository movieRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        movieRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void adminCanCreateMovie() throws Exception {
        mockMvc.perform(post("/api/admin/movies")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validMovieRequest("Interstellar")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.title").value("Interstellar"))
                .andExpect(jsonPath("$.genre").value("Sci-Fi"))
                .andExpect(jsonPath("$.durationMinutes").value(169))
                .andExpect(jsonPath("$.releaseDate").value("2014-11-07"));
    }

    @Test
    void userCannotCreateMovie() throws Exception {
        mockMvc.perform(post("/api/admin/movies")
                        .with(user("user@example.com").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validMovieRequest("Interstellar")))
                .andExpect(status().isForbidden());
    }

    @Test
    void guestCannotCreateMovie() throws Exception {
        mockMvc.perform(post("/api/admin/movies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validMovieRequest("Interstellar")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void guestCanGetMovies() throws Exception {
        createMovie("Interstellar");

        mockMvc.perform(get("/api/movies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Interstellar"));
    }

    @Test
    void guestCanGetMovieDetails() throws Exception {
        Long movieId = createMovie("Interstellar");

        mockMvc.perform(get("/api/movies/{id}", movieId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Interstellar"))
                .andExpect(jsonPath("$.description").value("A science fiction film about space exploration."));
    }

    @Test
    void missingMovieReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/movies/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Movie not found"));
    }

    @Test
    void invalidMovieRequestReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/admin/movies")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "",
                                  "description": "",
                                  "genre": "",
                                  "durationMinutes": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.validationErrors.title").exists())
                .andExpect(jsonPath("$.validationErrors.description").exists())
                .andExpect(jsonPath("$.validationErrors.genre").exists())
                .andExpect(jsonPath("$.validationErrors.durationMinutes").exists());
    }

    @Test
    void adminCanUpdateMovie() throws Exception {
        Long movieId = createMovie("Interstellar");

        mockMvc.perform(put("/api/admin/movies/{id}", movieId)
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validMovieRequest("Inception")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(movieId))
                .andExpect(jsonPath("$.title").value("Inception"));
    }

    @Test
    void adminCanDeleteMovie() throws Exception {
        Long movieId = createMovie("Interstellar");

        mockMvc.perform(delete("/api/admin/movies/{id}", movieId)
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/movies/{id}", movieId))
                .andExpect(status().isNotFound());
    }

    @Test
    void userCannotUpdateOrDeleteMovie() throws Exception {
        Long movieId = createMovie("Interstellar");

        mockMvc.perform(put("/api/admin/movies/{id}", movieId)
                        .with(user("user@example.com").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validMovieRequest("Inception")))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/admin/movies/{id}", movieId)
                        .with(user("user@example.com").roles("USER")))
                .andExpect(status().isForbidden());
    }

    private Long createMovie(String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/movies")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validMovieRequest(title)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private String validMovieRequest(String title) {
        return """
                {
                  "title": "%s",
                  "description": "A science fiction film about space exploration.",
                  "genre": "Sci-Fi",
                  "durationMinutes": 169,
                  "ageRating": "PG-13",
                  "director": "Christopher Nolan",
                  "releaseDate": "2014-11-07",
                  "posterUrl": "",
                  "trailerUrl": ""
                }
                """.formatted(title);
    }
}
