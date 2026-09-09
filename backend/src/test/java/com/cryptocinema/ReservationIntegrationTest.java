package com.cryptocinema;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

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

import com.cryptocinema.dto.ReservationRequest;
import com.cryptocinema.entity.Reservation;
import com.cryptocinema.entity.ReservationStatus;
import com.cryptocinema.entity.Role;
import com.cryptocinema.entity.ScreeningSeat;
import com.cryptocinema.entity.ScreeningSeatStatus;
import com.cryptocinema.entity.User;
import com.cryptocinema.exception.ConflictException;
import com.cryptocinema.repository.CinemaRepository;
import com.cryptocinema.repository.CityRepository;
import com.cryptocinema.repository.HallRepository;
import com.cryptocinema.repository.MovieRepository;
import com.cryptocinema.repository.ReservationRepository;
import com.cryptocinema.repository.ReservationSeatRepository;
import com.cryptocinema.repository.ScreeningRepository;
import com.cryptocinema.repository.ScreeningSeatRepository;
import com.cryptocinema.repository.SeatRepository;
import com.cryptocinema.repository.UserRepository;
import com.cryptocinema.service.ReservationService;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:reservation-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
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
class ReservationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ReservationSeatRepository reservationSeatRepository;

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
        reservationSeatRepository.deleteAll();
        reservationRepository.deleteAll();
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
    void userCanCreateReservationFromOwnHeldSeats() throws Exception {
        TestData data = createTestDataWithScreening();
        createUser("user-a@example.com", Role.USER);
        List<Long> seatIds = firstScreeningSeatIds(data.screeningId(), 2);
        holdSeat(data.screeningId(), seatIds.get(0), "user-a@example.com");
        holdSeat(data.screeningId(), seatIds.get(1), "user-a@example.com");

        mockMvc.perform(post("/api/reservations")
                        .with(user("user-a@example.com").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationRequest(data.screeningId(), seatIds)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.seatLabels", containsInAnyOrder("A1", "A2")));
    }

    @Test
    void totalAmountIsCalculatedByBackendForMultipleSeats() throws Exception {
        TestData data = createTestDataWithScreening();
        createUser("user-a@example.com", Role.USER);
        List<Long> seatIds = firstScreeningSeatIds(data.screeningId(), 3);
        for (Long seatId : seatIds) {
            holdSeat(data.screeningId(), seatId, "user-a@example.com");
        }

        mockMvc.perform(post("/api/reservations")
                        .with(user("user-a@example.com").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationRequest(data.screeningId(), seatIds)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketPrice").value(650))
                .andExpect(jsonPath("$.totalAmount").value(1950));
    }

    @Test
    void seatsBecomeReservedAndHoldDataIsClearedAfterCreate() throws Exception {
        TestData data = createTestDataWithScreening();
        createUser("user-a@example.com", Role.USER);
        Long seatId = firstScreeningSeatIds(data.screeningId(), 1).get(0);
        holdSeat(data.screeningId(), seatId, "user-a@example.com");

        createReservation(data.screeningId(), List.of(seatId), "user-a@example.com");

        ScreeningSeat screeningSeat = screeningSeatRepository.findById(seatId).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(screeningSeat.getStatus()).isEqualTo(ScreeningSeatStatus.RESERVED);
        org.assertj.core.api.Assertions.assertThat(screeningSeat.getHeldByUser()).isNull();
        org.assertj.core.api.Assertions.assertThat(screeningSeat.getHoldExpiresAt()).isNull();
    }

    @Test
    void userCannotReserveAnotherUsersHeldSeat() throws Exception {
        TestData data = createTestDataWithScreening();
        createUser("user-a@example.com", Role.USER);
        createUser("user-b@example.com", Role.USER);
        Long seatId = firstScreeningSeatIds(data.screeningId(), 1).get(0);
        holdSeat(data.screeningId(), seatId, "user-a@example.com");

        mockMvc.perform(post("/api/reservations")
                        .with(user("user-b@example.com").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationRequest(data.screeningId(), List.of(seatId))))
                .andExpect(status().isForbidden());
    }

    @Test
    void userCannotReserveAvailableSeatWithoutHold() throws Exception {
        TestData data = createTestDataWithScreening();
        createUser("user-a@example.com", Role.USER);
        Long seatId = firstScreeningSeatIds(data.screeningId(), 1).get(0);

        mockMvc.perform(post("/api/reservations")
                        .with(user("user-a@example.com").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationRequest(data.screeningId(), List.of(seatId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Seat must be held before reservation."));
    }

    @Test
    void userCannotReserveReservedSeat() throws Exception {
        TestData data = createTestDataWithScreening();
        createUser("user-a@example.com", Role.USER);
        Long seatId = firstScreeningSeatIds(data.screeningId(), 1).get(0);
        holdSeat(data.screeningId(), seatId, "user-a@example.com");
        createReservation(data.screeningId(), List.of(seatId), "user-a@example.com");

        mockMvc.perform(post("/api/reservations")
                        .with(user("user-a@example.com").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationRequest(data.screeningId(), List.of(seatId))))
                .andExpect(status().isConflict());
    }

    @Test
    void seatsFromDifferentScreeningCannotBeReservedTogether() throws Exception {
        TestData data = createTestDataWithScreening();
        Long secondScreeningId = createScreening(data.movieId(), data.hallId(), futureStart(23, 34));
        createUser("user-a@example.com", Role.USER);
        Long firstSeatId = firstScreeningSeatIds(data.screeningId(), 1).get(0);
        Long secondSeatId = firstScreeningSeatIds(secondScreeningId, 1).get(0);
        holdSeat(data.screeningId(), firstSeatId, "user-a@example.com");
        holdSeat(secondScreeningId, secondSeatId, "user-a@example.com");

        mockMvc.perform(post("/api/reservations")
                        .with(user("user-a@example.com").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationRequest(data.screeningId(), List.of(firstSeatId, secondSeatId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("All seats must belong to the selected screening."));
    }

    @Test
    void expiredHoldCannotBecomeReservation() throws Exception {
        TestData data = createTestDataWithScreening();
        createUser("user-a@example.com", Role.USER);
        Long seatId = firstScreeningSeatIds(data.screeningId(), 1).get(0);
        holdSeat(data.screeningId(), seatId, "user-a@example.com");
        ScreeningSeat screeningSeat = screeningSeatRepository.findById(seatId).orElseThrow();
        screeningSeat.setHoldExpiresAt(LocalDateTime.now().minusMinutes(1));
        screeningSeatRepository.save(screeningSeat);

        mockMvc.perform(post("/api/reservations")
                        .with(user("user-a@example.com").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationRequest(data.screeningId(), List.of(seatId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Seat hold has expired."));
    }

    @Test
    void guestCannotCreateReservation() throws Exception {
        TestData data = createTestDataWithScreening();
        Long seatId = firstScreeningSeatIds(data.screeningId(), 1).get(0);

        mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationRequest(data.screeningId(), List.of(seatId))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userCanSeeOwnReservations() throws Exception {
        TestData data = createTestDataWithScreening();
        createUser("user-a@example.com", Role.USER);
        Long seatId = firstScreeningSeatIds(data.screeningId(), 1).get(0);
        holdSeat(data.screeningId(), seatId, "user-a@example.com");
        createReservation(data.screeningId(), List.of(seatId), "user-a@example.com");

        mockMvc.perform(get("/api/reservations/me")
                        .with(user("user-a@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].movieTitle").value("Interstellar"));
    }

    @Test
    void userCannotSeeAnotherUsersReservation() throws Exception {
        TestData data = createTestDataWithScreening();
        createUser("user-a@example.com", Role.USER);
        createUser("user-b@example.com", Role.USER);
        Long seatId = firstScreeningSeatIds(data.screeningId(), 1).get(0);
        holdSeat(data.screeningId(), seatId, "user-a@example.com");
        Long reservationId = createReservation(data.screeningId(), List.of(seatId), "user-a@example.com");

        mockMvc.perform(get("/api/reservations/{id}", reservationId)
                        .with(user("user-b@example.com").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancelPendingPaymentReservationReturnsSeatsToAvailable() throws Exception {
        TestData data = createTestDataWithScreening();
        createUser("user-a@example.com", Role.USER);
        Long seatId = firstScreeningSeatIds(data.screeningId(), 1).get(0);
        holdSeat(data.screeningId(), seatId, "user-a@example.com");
        Long reservationId = createReservation(data.screeningId(), List.of(seatId), "user-a@example.com");

        mockMvc.perform(post("/api/reservations/{id}/cancel", reservationId)
                        .with(user("user-a@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        org.assertj.core.api.Assertions.assertThat(screeningSeatRepository.findById(seatId).orElseThrow().getStatus())
                .isEqualTo(ScreeningSeatStatus.AVAILABLE);
    }

    @Test
    void reservationExpirationReturnsSeatsToAvailable() throws Exception {
        TestData data = createTestDataWithScreening();
        createUser("user-a@example.com", Role.USER);
        Long seatId = firstScreeningSeatIds(data.screeningId(), 1).get(0);
        holdSeat(data.screeningId(), seatId, "user-a@example.com");
        Long reservationId = createReservation(data.screeningId(), List.of(seatId), "user-a@example.com");
        Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
        reservation.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        reservationRepository.save(reservation);

        mockMvc.perform(get("/api/screenings/{id}/seats", data.screeningId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("AVAILABLE"));

        org.assertj.core.api.Assertions.assertThat(reservationRepository.findById(reservationId).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.EXPIRED);
    }

    @Test
    void confirmedReservationDoesNotExpire() throws Exception {
        TestData data = createTestDataWithScreening();
        createUser("user-a@example.com", Role.USER);
        Long seatId = firstScreeningSeatIds(data.screeningId(), 1).get(0);
        holdSeat(data.screeningId(), seatId, "user-a@example.com");
        Long reservationId = createReservation(data.screeningId(), List.of(seatId), "user-a@example.com");
        Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservation.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        reservationRepository.save(reservation);

        mockMvc.perform(get("/api/reservations/me")
                        .with(user("user-a@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("CONFIRMED"));

        org.assertj.core.api.Assertions.assertThat(screeningSeatRepository.findById(seatId).orElseThrow().getStatus())
                .isEqualTo(ScreeningSeatStatus.RESERVED);
    }

    @Test
    void concurrentReservationAttemptsForSameSeatCannotBothSucceed() throws Exception {
        TestData data = createTestDataWithScreening();
        createUser("user-a@example.com", Role.USER);
        Long seatId = firstScreeningSeatIds(data.screeningId(), 1).get(0);
        holdSeat(data.screeningId(), seatId, "user-a@example.com");

        Callable<Boolean> first = () -> reserveThroughService(data.screeningId(), List.of(seatId), "user-a@example.com");
        Callable<Boolean> second = () -> reserveThroughService(data.screeningId(), List.of(seatId), "user-a@example.com");
        var executor = Executors.newFixedThreadPool(2);
        try {
            long successCount = executor.invokeAll(List.of(first, second)).stream()
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
    void screeningWithActiveReservationCannotBeDeleted() throws Exception {
        TestData data = createTestDataWithScreening();
        createUser("user-a@example.com", Role.USER);
        Long seatId = firstScreeningSeatIds(data.screeningId(), 1).get(0);
        holdSeat(data.screeningId(), seatId, "user-a@example.com");
        createReservation(data.screeningId(), List.of(seatId), "user-a@example.com");

        mockMvc.perform(delete("/api/admin/screenings/{id}", data.screeningId())
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Screening cannot be deleted while it has active reservations."));
    }

    private TestData createTestDataWithScreening() throws Exception {
        Long cityId = createCity();
        Long cinemaId = createCinema(cityId);
        Long hallId = createHall(cinemaId);
        Long movieId = createMovie();
        generateSeats(hallId);
        Long screeningId = createScreening(movieId, hallId, futureStart(20, 30));
        return new TestData(cityId, cinemaId, hallId, movieId, screeningId);
    }

    private Long createCity() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/cities")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Novi Sad\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private Long createCinema(Long cityId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/cinemas")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Arena Cineplex","address":"Bulevar Mihajla Pupina 3","cityId":%d}
                                """.formatted(cityId)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private Long createHall(Long cinemaId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/halls")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sala 1\",\"cinemaId\":%d}".formatted(cinemaId)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private Long createMovie() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/movies")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Interstellar",
                                  "description": "A science fiction film about space exploration.",
                                  "genre": "Sci-Fi",
                                  "durationMinutes": 169,
                                  "ageRating": "PG-13",
                                  "director": "Christopher Nolan",
                                  "releaseDate": "2014-11-07",
                                  "posterUrl": "",
                                  "trailerUrl": ""
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private void generateSeats(Long hallId) throws Exception {
        mockMvc.perform(post("/api/admin/halls/{hallId}/seats/generate", hallId)
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rows\":2,\"seatsPerRow\":3}"))
                .andExpect(status().isCreated());
    }

    private Long createScreening(Long movieId, Long hallId, LocalDateTime startTime) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/screenings")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"movieId":%d,"hallId":%d,"startTime":"%s","ticketPrice":650}
                                """.formatted(movieId, hallId, startTime)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private void holdSeat(Long screeningId, Long screeningSeatId, String email) throws Exception {
        mockMvc.perform(post("/api/screenings/{screeningId}/seats/{screeningSeatId}/hold", screeningId, screeningSeatId)
                        .with(user(email).roles("USER")))
                .andExpect(status().isOk());
    }

    private Long createReservation(Long screeningId, List<Long> seatIds, String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/reservations")
                        .with(user(email).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationRequest(screeningId, seatIds)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("reservationId").asLong();
    }

    private boolean reserveThroughService(Long screeningId, List<Long> seatIds, String email) {
        try {
            reservationService.create(new ReservationRequest(screeningId, seatIds), authentication(email));
            return true;
        } catch (ConflictException exception) {
            return false;
        }
    }

    private List<Long> firstScreeningSeatIds(Long screeningId, int count) {
        return screeningSeatRepository.findByScreeningIdOrderBySeatRowLabelAscSeatSeatNumberAsc(screeningId).stream()
                .limit(count)
                .map(ScreeningSeat::getId)
                .toList();
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

    private String reservationRequest(Long screeningId, List<Long> seatIds) {
        return """
                {"screeningId":%d,"screeningSeatIds":%s}
                """.formatted(screeningId, seatIds);
    }

    private LocalDateTime futureStart(int hour, int minute) {
        return LocalDate.now().plusDays(2).atTime(hour, minute);
    }

    private record TestData(Long cityId, Long cinemaId, Long hallId, Long movieId, Long screeningId) {
    }
}
