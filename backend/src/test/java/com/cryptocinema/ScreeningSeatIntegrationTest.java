package com.cryptocinema;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.cryptocinema.entity.Role;
import com.cryptocinema.entity.ScreeningSeat;
import com.cryptocinema.entity.ScreeningSeatStatus;
import com.cryptocinema.entity.User;
import com.cryptocinema.exception.ConflictException;
import com.cryptocinema.repository.CinemaRepository;
import com.cryptocinema.repository.CityRepository;
import com.cryptocinema.repository.HallRepository;
import com.cryptocinema.repository.MovieRepository;
import com.cryptocinema.repository.ScreeningSeatRepository;
import com.cryptocinema.repository.ScreeningRepository;
import com.cryptocinema.repository.SeatRepository;
import com.cryptocinema.repository.UserRepository;
import com.cryptocinema.service.ScreeningSeatService;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:screening-seat-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
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
class ScreeningSeatIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ScreeningSeatService screeningSeatService;

    @Autowired
    private ScreeningSeatRepository screeningSeatRepository;

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
        screeningSeatRepository.deleteAll();
        screeningRepository.deleteAll();
        seatRepository.deleteAll();
        hallRepository.deleteAll();
        cinemaRepository.deleteAll();
        cityRepository.deleteAll();
        movieRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void creatingScreeningCreatesScreeningSeatsForAllHallSeats() throws Exception {
        TestData data = createTestData();

        Long screeningId = createScreening(data.movieId(), data.hallId(), futureStart(20, 30));

        mockMvc.perform(get("/api/screenings/{screeningId}/seats", screeningId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[0].rowLabel").value("A"))
                .andExpect(jsonPath("$[0].seatNumber").value(1))
                .andExpect(jsonPath("$[0].status").value("AVAILABLE"));
    }

    @Test
    void screeningCannotBeCreatedForHallWithoutSeats() throws Exception {
        Long cityId = createCity("Novi Sad");
        Long cinemaId = createCinema(cityId);
        Long hallId = createHall(cinemaId, "Sala 1");
        Long movieId = createMovie("Interstellar", 169);

        mockMvc.perform(post("/api/admin/screenings")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(screeningRequest(movieId, hallId, futureStart(20, 30), "650")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Screening cannot be created for a hall without seats."));
    }

    @Test
    void guestCanSeeSeatAvailability() throws Exception {
        TestData data = createTestDataWithScreening();

        mockMvc.perform(get("/api/screenings/{screeningId}/seats", data.screeningId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)));
    }

    @Test
    void guestCannotHoldSeat() throws Exception {
        TestData data = createTestDataWithScreening();
        Long screeningSeatId = firstScreeningSeatId(data.screeningId());

        mockMvc.perform(post("/api/screenings/{screeningId}/seats/{screeningSeatId}/hold",
                        data.screeningId(), screeningSeatId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userCanHoldAvailableSeat() throws Exception {
        TestData data = createTestDataWithScreening();
        createUser("user-a@example.com", Role.USER);
        Long screeningSeatId = firstScreeningSeatId(data.screeningId());

        mockMvc.perform(post("/api/screenings/{screeningId}/seats/{screeningSeatId}/hold",
                        data.screeningId(), screeningSeatId)
                        .with(user("user-a@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HELD"))
                .andExpect(jsonPath("$.heldByCurrentUser").value(true))
                .andExpect(jsonPath("$.holdExpiresAt").exists());
    }

    @Test
    void holdStoresCurrentUserAndExpiration() throws Exception {
        TestData data = createTestDataWithScreening();
        User user = createUser("user-a@example.com", Role.USER);
        Long screeningSeatId = firstScreeningSeatId(data.screeningId());

        mockMvc.perform(post("/api/screenings/{screeningId}/seats/{screeningSeatId}/hold",
                        data.screeningId(), screeningSeatId)
                        .with(user("user-a@example.com").roles("USER")))
                .andExpect(status().isOk());

        ScreeningSeat screeningSeat = screeningSeatRepository.findById(screeningSeatId).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(screeningSeat.getHeldByUser().getId()).isEqualTo(user.getId());
        org.assertj.core.api.Assertions.assertThat(screeningSeat.getHoldExpiresAt()).isAfter(LocalDateTime.now());
    }

    @Test
    void userCanReleaseOwnHold() throws Exception {
        TestData data = createTestDataWithScreening();
        createUser("user-a@example.com", Role.USER);
        Long screeningSeatId = firstScreeningSeatId(data.screeningId());
        holdSeat(data.screeningId(), screeningSeatId, "user-a@example.com");

        mockMvc.perform(delete("/api/screenings/{screeningId}/seats/{screeningSeatId}/hold",
                        data.screeningId(), screeningSeatId)
                        .with(user("user-a@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.heldByCurrentUser").value(false));
    }

    @Test
    void userCannotReleaseAnotherUsersHold() throws Exception {
        TestData data = createTestDataWithScreening();
        createUser("user-a@example.com", Role.USER);
        createUser("user-b@example.com", Role.USER);
        Long screeningSeatId = firstScreeningSeatId(data.screeningId());
        holdSeat(data.screeningId(), screeningSeatId, "user-a@example.com");

        mockMvc.perform(delete("/api/screenings/{screeningId}/seats/{screeningSeatId}/hold",
                        data.screeningId(), screeningSeatId)
                        .with(user("user-b@example.com").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void heldSeatCannotBeHeldByAnotherUser() throws Exception {
        TestData data = createTestDataWithScreening();
        createUser("user-a@example.com", Role.USER);
        createUser("user-b@example.com", Role.USER);
        Long screeningSeatId = firstScreeningSeatId(data.screeningId());
        holdSeat(data.screeningId(), screeningSeatId, "user-a@example.com");

        mockMvc.perform(post("/api/screenings/{screeningId}/seats/{screeningSeatId}/hold",
                        data.screeningId(), screeningSeatId)
                        .with(user("user-b@example.com").roles("USER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This seat is no longer available."));
    }

    @Test
    void reservedSeatCannotBeHeld() throws Exception {
        TestData data = createTestDataWithScreening();
        createUser("user-a@example.com", Role.USER);
        Long screeningSeatId = firstScreeningSeatId(data.screeningId());
        ScreeningSeat screeningSeat = screeningSeatRepository.findById(screeningSeatId).orElseThrow();
        screeningSeat.setStatus(ScreeningSeatStatus.RESERVED);
        screeningSeatRepository.save(screeningSeat);

        mockMvc.perform(post("/api/screenings/{screeningId}/seats/{screeningSeatId}/hold",
                        data.screeningId(), screeningSeatId)
                        .with(user("user-a@example.com").roles("USER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This seat is no longer available."));
    }

    @Test
    void expiredHeldSeatBecomesAvailable() throws Exception {
        TestData data = createTestDataWithScreening();
        createUser("user-a@example.com", Role.USER);
        Long screeningSeatId = firstScreeningSeatId(data.screeningId());
        holdSeat(data.screeningId(), screeningSeatId, "user-a@example.com");
        ScreeningSeat screeningSeat = screeningSeatRepository.findById(screeningSeatId).orElseThrow();
        screeningSeat.setHoldExpiresAt(LocalDateTime.now().minusMinutes(1));
        screeningSeatRepository.save(screeningSeat);

        mockMvc.perform(get("/api/screenings/{screeningId}/seats", data.screeningId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$[0].heldByCurrentUser").value(false))
                .andExpect(jsonPath("$[0].holdExpiresAt").doesNotExist());
    }

    @Test
    void concurrentHoldAttemptsForSameSeatCannotBothSucceed() throws Exception {
        TestData data = createTestDataWithScreening();
        createUser("user-a@example.com", Role.USER);
        createUser("user-b@example.com", Role.USER);
        Long screeningSeatId = firstScreeningSeatId(data.screeningId());

        Callable<Boolean> firstHold = () -> holdThroughService(data.screeningId(), screeningSeatId, "user-a@example.com");
        Callable<Boolean> secondHold = () -> holdThroughService(data.screeningId(), screeningSeatId, "user-b@example.com");
        var executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> results = executor.invokeAll(List.of(firstHold, secondHold));
            long successCount = results.stream()
                    .filter(result -> {
                        try {
                            return result.get();
                        } catch (Exception exception) {
                            throw new RuntimeException(exception);
                        }
                    })
                    .count();
            org.assertj.core.api.Assertions.assertThat(successCount).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void differentSeatsCanBeHeldAtSameTime() throws Exception {
        TestData data = createTestDataWithScreening();
        createUser("user-a@example.com", Role.USER);
        createUser("user-b@example.com", Role.USER);
        List<ScreeningSeat> seats = screeningSeatRepository.findByScreeningIdOrderBySeatRowLabelAscSeatSeatNumberAsc(data.screeningId());

        holdSeat(data.screeningId(), seats.get(0).getId(), "user-a@example.com");
        holdSeat(data.screeningId(), seats.get(1).getId(), "user-b@example.com");

        org.assertj.core.api.Assertions.assertThat(
                screeningSeatRepository.findByScreeningId(data.screeningId()).stream()
                        .filter(screeningSeat -> screeningSeat.getStatus() == ScreeningSeatStatus.HELD)
                        .count()).isEqualTo(2);
    }

    @Test
    void guestSeatResponseDoesNotExposeUserData() throws Exception {
        TestData data = createTestDataWithScreening();
        createUser("user-a@example.com", Role.USER);
        Long screeningSeatId = firstScreeningSeatId(data.screeningId());
        holdSeat(data.screeningId(), screeningSeatId, "user-a@example.com");

        mockMvc.perform(get("/api/screenings/{screeningId}/seats", data.screeningId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].heldByUser").doesNotExist())
                .andExpect(jsonPath("$[0].email").doesNotExist())
                .andExpect(jsonPath("$[0].heldByCurrentUser").value(false))
                .andExpect(jsonPath("$[0].holdExpiresAt").doesNotExist());
    }

    @Test
    void existingScreeningsCanInitializeMissingSeatsWithoutDuplicates() throws Exception {
        TestData data = createTestDataWithScreening();
        screeningSeatRepository.deleteByScreeningId(data.screeningId());

        mockMvc.perform(get("/api/screenings/{screeningId}/seats", data.screeningId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)));
        mockMvc.perform(get("/api/screenings/{screeningId}/seats", data.screeningId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)));

        org.assertj.core.api.Assertions.assertThat(screeningSeatRepository.countByScreeningId(data.screeningId()))
                .isEqualTo(6);
    }

    @Test
    void screeningDeleteAndHallUpdateAreBlockedWhenSeatsAreHeld() throws Exception {
        TestData data = createTestDataWithScreening();
        createUser("user-a@example.com", Role.USER);
        Long secondHallId = createHall(data.cinemaId(), "Sala 2");
        generateSeats(secondHallId, 2, 3);
        Long screeningSeatId = firstScreeningSeatId(data.screeningId());
        holdSeat(data.screeningId(), screeningSeatId, "user-a@example.com");

        mockMvc.perform(delete("/api/admin/screenings/{id}", data.screeningId())
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Screening cannot be deleted while it has held or reserved seats."));

        mockMvc.perform(put("/api/admin/screenings/{id}", data.screeningId())
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(screeningRequest(data.movieId(), secondHallId, futureStart(20, 30), "650")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Screening hall cannot be changed while it has held or reserved seats."));
    }

    private TestData createTestDataWithScreening() throws Exception {
        TestData data = createTestData();
        Long screeningId = createScreening(data.movieId(), data.hallId(), futureStart(20, 30));
        return new TestData(data.cityId(), data.cinemaId(), data.hallId(), data.movieId(), screeningId);
    }

    private TestData createTestData() throws Exception {
        Long cityId = createCity("Novi Sad");
        Long cinemaId = createCinema(cityId);
        Long hallId = createHall(cinemaId, "Sala 1");
        Long movieId = createMovie("Interstellar", 169);
        generateSeats(hallId, 2, 3);
        return new TestData(cityId, cinemaId, hallId, movieId, null);
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

    private void generateSeats(Long hallId, int rows, int seatsPerRow) throws Exception {
        mockMvc.perform(post("/api/admin/halls/{hallId}/seats/generate", hallId)
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rows": %d,
                                  "seatsPerRow": %d
                                }
                                """.formatted(rows, seatsPerRow)))
                .andExpect(status().isCreated());
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

    private void holdSeat(Long screeningId, Long screeningSeatId, String email) throws Exception {
        mockMvc.perform(post("/api/screenings/{screeningId}/seats/{screeningSeatId}/hold", screeningId, screeningSeatId)
                        .with(user(email).roles("USER")))
                .andExpect(status().isOk());
    }

    private boolean holdThroughService(Long screeningId, Long screeningSeatId, String email) {
        try {
            screeningSeatService.hold(screeningId, screeningSeatId, authentication(email));
            return true;
        } catch (ConflictException exception) {
            return false;
        }
    }

    private Long firstScreeningSeatId(Long screeningId) {
        return screeningSeatRepository.findByScreeningIdOrderBySeatRowLabelAscSeatSeatNumberAsc(screeningId)
                .get(0)
                .getId();
    }

    private User createUser(String email, Role role) {
        User user = new User();
        user.setFirstName("Test");
        user.setLastName("User");
        user.setEmail(email);
        user.setPassword("password");
        user.setRole(role);
        return userRepository.save(user);
    }

    private Authentication authentication(String email) {
        return new UsernamePasswordAuthenticationToken(
                email,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
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

    private LocalDateTime futureStart(int hour, int minute) {
        return LocalDate.now().plusDays(2).atTime(hour, minute);
    }

    private record TestData(Long cityId, Long cinemaId, Long hallId, Long movieId, Long screeningId) {
    }
}
