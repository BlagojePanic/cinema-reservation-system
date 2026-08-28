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

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.cryptocinema.repository.CinemaRepository;
import com.cryptocinema.repository.CityRepository;
import com.cryptocinema.repository.HallRepository;
import com.cryptocinema.repository.MovieRepository;
import com.cryptocinema.repository.ScreeningRepository;
import com.cryptocinema.repository.SeatRepository;
import com.cryptocinema.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:screening-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
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
class ScreeningIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ScreeningRepository screeningRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private HallRepository hallRepository;

    @Autowired
    private CinemaRepository cinemaRepository;

    @Autowired
    private CityRepository cityRepository;

    @Autowired
    private MovieRepository movieRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        screeningRepository.deleteAll();
        seatRepository.deleteAll();
        hallRepository.deleteAll();
        cinemaRepository.deleteAll();
        cityRepository.deleteAll();
        movieRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void adminCanCreateScreening() throws Exception {
        TestData data = createTestData();

        mockMvc.perform(post("/api/admin/screenings")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(screeningRequest(data.movieId(), data.hallId(), futureStart(20, 30), "650")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.movieId").value(data.movieId()))
                .andExpect(jsonPath("$.movieTitle").value("Interstellar"))
                .andExpect(jsonPath("$.hallId").value(data.hallId()))
                .andExpect(jsonPath("$.hallName").value("Sala 1"))
                .andExpect(jsonPath("$.cinemaName").value("Arena Cineplex"))
                .andExpect(jsonPath("$.cityName").value("Novi Sad"))
                .andExpect(jsonPath("$.ticketPrice").value(650));
    }

    @Test
    void userCannotCreateScreening() throws Exception {
        TestData data = createTestData();

        mockMvc.perform(post("/api/admin/screenings")
                        .with(user("user@example.com").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(screeningRequest(data.movieId(), data.hallId(), futureStart(20, 30), "650")))
                .andExpect(status().isForbidden());
    }

    @Test
    void guestCannotCreateScreening() throws Exception {
        TestData data = createTestData();

        mockMvc.perform(post("/api/admin/screenings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(screeningRequest(data.movieId(), data.hallId(), futureStart(20, 30), "650")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void guestCanGetScreenings() throws Exception {
        TestData data = createTestData();
        createScreening(data.movieId(), data.hallId(), futureStart(20, 30));

        mockMvc.perform(get("/api/screenings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].movieTitle").value("Interstellar"));
    }

    @Test
    void screeningRequiresValidMovie() throws Exception {
        TestData data = createTestData();

        mockMvc.perform(post("/api/admin/screenings")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(screeningRequest(9999L, data.hallId(), futureStart(20, 30), "650")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Movie not found"));
    }

    @Test
    void screeningRequiresValidHall() throws Exception {
        TestData data = createTestData();

        mockMvc.perform(post("/api/admin/screenings")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(screeningRequest(data.movieId(), 9999L, futureStart(20, 30), "650")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Hall not found"));
    }

    @Test
    void ticketPriceMustBePositive() throws Exception {
        TestData data = createTestData();

        mockMvc.perform(post("/api/admin/screenings")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(screeningRequest(data.movieId(), data.hallId(), futureStart(20, 30), "0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.ticketPrice").exists());
    }

    @Test
    void overlappingScreeningsInSameHallReturnConflict() throws Exception {
        TestData data = createTestData();
        createScreening(data.movieId(), data.hallId(), futureStart(20, 30));

        mockMvc.perform(post("/api/admin/screenings")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(screeningRequest(data.movieId(), data.hallId(), futureStart(22, 0), "650")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Screening conflicts with another screening in this hall."));
    }

    @Test
    void differentHallsCanHaveScreeningsAtSameTime() throws Exception {
        TestData data = createTestData();
        Long secondHallId = createHall(data.cinemaId(), "Sala 2");
        createScreening(data.movieId(), data.hallId(), futureStart(20, 30));

        mockMvc.perform(post("/api/admin/screenings")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(screeningRequest(data.movieId(), secondHallId, futureStart(20, 30), "650")))
                .andExpect(status().isCreated());
    }

    @Test
    void screeningAfterDurationAndBufferIsAllowed() throws Exception {
        TestData data = createTestData();
        createScreening(data.movieId(), data.hallId(), futureStart(20, 30));

        mockMvc.perform(post("/api/admin/screenings")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(screeningRequest(data.movieId(), data.hallId(), futureStart(23, 34), "650")))
                .andExpect(status().isCreated());
    }

    @Test
    void adminCanUpdateScreening() throws Exception {
        TestData data = createTestData();
        Long screeningId = createScreening(data.movieId(), data.hallId(), futureStart(20, 30));

        mockMvc.perform(put("/api/admin/screenings/{id}", screeningId)
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(screeningRequest(data.movieId(), data.hallId(), futureStart(19, 0), "700")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(screeningId))
                .andExpect(jsonPath("$.ticketPrice").value(700));
    }

    @Test
    void updateChecksConflict() throws Exception {
        TestData data = createTestData();
        createScreening(data.movieId(), data.hallId(), futureStart(20, 30));
        Long secondScreeningId = createScreening(data.movieId(), data.hallId(), futureStart(23, 34));

        mockMvc.perform(put("/api/admin/screenings/{id}", secondScreeningId)
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(screeningRequest(data.movieId(), data.hallId(), futureStart(22, 0), "650")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Screening conflicts with another screening in this hall."));
    }

    @Test
    void adminCanDeleteScreening() throws Exception {
        TestData data = createTestData();
        Long screeningId = createScreening(data.movieId(), data.hallId(), futureStart(20, 30));

        mockMvc.perform(delete("/api/admin/screenings/{id}", screeningId)
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/screenings/{id}", screeningId))
                .andExpect(status().isNotFound());
    }

    @Test
    void movieWithScreeningCannotBeDeleted() throws Exception {
        TestData data = createTestData();
        createScreening(data.movieId(), data.hallId(), futureStart(20, 30));

        mockMvc.perform(delete("/api/admin/movies/{id}", data.movieId())
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Movie cannot be deleted while it has screenings"));
    }

    @Test
    void hallWithScreeningCannotBeDeleted() throws Exception {
        TestData data = createTestData();
        createScreening(data.movieId(), data.hallId(), futureStart(20, 30));

        mockMvc.perform(delete("/api/admin/halls/{id}", data.hallId())
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Hall cannot be deleted while it has screenings"));
    }

    @Test
    void publicFilterByMovieDateAndCityWorks() throws Exception {
        TestData data = createTestData();
        Long otherMovieId = createMovie("Inception", 148);
        createScreening(data.movieId(), data.hallId(), futureStart(20, 30));
        createScreening(otherMovieId, data.hallId(), futureStart(23, 34));

        mockMvc.perform(get("/api/screenings")
                        .param("cityId", data.cityId().toString())
                        .param("movieId", data.movieId().toString())
                        .param("date", futureDate().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].movieTitle").value("Interstellar"))
                .andExpect(jsonPath("$[0].cityName").value("Novi Sad"));
    }

    private TestData createTestData() throws Exception {
        Long cityId = createCity("Novi Sad");
        Long cinemaId = createCinema(cityId);
        Long hallId = createHall(cinemaId, "Sala 1");
        Long movieId = createMovie("Interstellar", 169);
        return new TestData(cityId, cinemaId, hallId, movieId);
    }

    private Long createCity(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/cities")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s"
                                }
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private Long createCinema(Long cityId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/cinemas")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Arena Cineplex",
                                  "address": "Bulevar Mihajla Pupina 3",
                                  "cityId": %d
                                }
                                """.formatted(cityId)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private Long createHall(Long cinemaId, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/halls")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "cinemaId": %d
                                }
                                """.formatted(name, cinemaId)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private Long createMovie(String title, int durationMinutes) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/movies")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "%s",
                                  "description": "A science fiction film about space exploration.",
                                  "genre": "Sci-Fi",
                                  "durationMinutes": %d,
                                  "ageRating": "PG-13",
                                  "director": "Christopher Nolan",
                                  "releaseDate": "2014-11-07",
                                  "posterUrl": "",
                                  "trailerUrl": ""
                                }
                                """.formatted(title, durationMinutes)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private Long createScreening(Long movieId, Long hallId, LocalDateTime startTime) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/screenings")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(screeningRequest(movieId, hallId, startTime, "650")))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private String screeningRequest(Long movieId, Long hallId, LocalDateTime startTime, String ticketPrice) {
        return """
                {
                  "movieId": %d,
                  "hallId": %d,
                  "startTime": "%s",
                  "ticketPrice": %s
                }
                """.formatted(movieId, hallId, startTime, ticketPrice);
    }

    private LocalDate futureDate() {
        return LocalDate.now().plusDays(2);
    }

    private LocalDateTime futureStart(int hour, int minute) {
        return futureDate().atTime(hour, minute);
    }

    private record TestData(Long cityId, Long cinemaId, Long hallId, Long movieId) {
    }
}
