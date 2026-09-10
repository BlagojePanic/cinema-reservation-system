package com.cryptocinema;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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

import com.cryptocinema.dto.PaymentRequest;
import com.cryptocinema.entity.PaymentMethod;
import com.cryptocinema.entity.PaymentStatus;
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
import com.cryptocinema.repository.PaymentRepository;
import com.cryptocinema.repository.ReservationRepository;
import com.cryptocinema.repository.ReservationSeatRepository;
import com.cryptocinema.repository.ScreeningRepository;
import com.cryptocinema.repository.ScreeningSeatRepository;
import com.cryptocinema.repository.SeatRepository;
import com.cryptocinema.repository.TicketRepository;
import com.cryptocinema.repository.UserRepository;
import com.cryptocinema.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:payment-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
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
class PaymentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private TicketRepository ticketRepository;

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
        ticketRepository.deleteAll();
        paymentRepository.deleteAll();
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
    void userCanPayOwnPendingPaymentReservation() throws Exception {
        ReservationFixture fixture = createPendingReservation("user-a@example.com", 2);

        mockMvc.perform(post("/api/reservations/{id}/payment", fixture.reservationId())
                        .with(user("user-a@example.com").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(successPaymentRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.method").value("CARD_SIMULATION"));
    }

    @Test
    void guestCannotPayReservation() throws Exception {
        ReservationFixture fixture = createPendingReservation("user-a@example.com", 1);

        mockMvc.perform(post("/api/reservations/{id}/payment", fixture.reservationId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(successPaymentRequest()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userCannotPayAnotherUsersReservation() throws Exception {
        ReservationFixture fixture = createPendingReservation("user-a@example.com", 1);
        createUser("user-b@example.com", Role.USER);

        mockMvc.perform(post("/api/reservations/{id}/payment", fixture.reservationId())
                        .with(user("user-b@example.com").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(successPaymentRequest()))
                .andExpect(status().isForbidden());
    }

    @Test
    void amountIsTakenFromBackendReservationTotalAmount() throws Exception {
        ReservationFixture fixture = createPendingReservation("user-a@example.com", 3);

        mockMvc.perform(post("/api/reservations/{id}/payment", fixture.reservationId())
                        .with(user("user-a@example.com").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(successPaymentRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(1950))
                .andExpect(jsonPath("$.currency").value("RSD"));
    }

    @Test
    void successfulPaymentConfirmsReservationAndKeepsSeatsReserved() throws Exception {
        ReservationFixture fixture = createPendingReservation("user-a@example.com", 1);

        pay(fixture.reservationId(), "user-a@example.com", true);

        org.assertj.core.api.Assertions.assertThat(reservationRepository.findById(fixture.reservationId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.CONFIRMED);
        org.assertj.core.api.Assertions.assertThat(screeningSeatRepository.findById(fixture.screeningSeatIds().get(0)).orElseThrow().getStatus())
                .isEqualTo(ScreeningSeatStatus.RESERVED);
    }

    @Test
    void confirmedReservationDoesNotExpireAfterSuccessfulPayment() throws Exception {
        ReservationFixture fixture = createPendingReservation("user-a@example.com", 1);
        pay(fixture.reservationId(), "user-a@example.com", true);
        Reservation reservation = reservationRepository.findById(fixture.reservationId()).orElseThrow();
        reservation.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        reservationRepository.save(reservation);

        mockMvc.perform(get("/api/reservations/me")
                        .with(user("user-a@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("CONFIRMED"));

        org.assertj.core.api.Assertions.assertThat(screeningSeatRepository.findById(fixture.screeningSeatIds().get(0)).orElseThrow().getStatus())
                .isEqualTo(ScreeningSeatStatus.RESERVED);
    }

    @Test
    void failedPaymentKeepsReservationPendingAndAllowsRetry() throws Exception {
        ReservationFixture fixture = createPendingReservation("user-a@example.com", 1);

        mockMvc.perform(post("/api/reservations/{id}/payment", fixture.reservationId())
                        .with(user("user-a@example.com").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(failedPaymentRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));

        org.assertj.core.api.Assertions.assertThat(reservationRepository.findById(fixture.reservationId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.PENDING_PAYMENT);

        mockMvc.perform(post("/api/reservations/{id}/payment", fixture.reservationId())
                        .with(user("user-a@example.com").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(successPaymentRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void successfulPaymentPreventsNewPayment() throws Exception {
        ReservationFixture fixture = createPendingReservation("user-a@example.com", 1);
        pay(fixture.reservationId(), "user-a@example.com", true);

        mockMvc.perform(post("/api/reservations/{id}/payment", fixture.reservationId())
                        .with(user("user-a@example.com").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(successPaymentRequest()))
                .andExpect(status().isConflict());
    }

    @Test
    void expiredReservationCannotBePaidAndSeatsAreReleased() throws Exception {
        ReservationFixture fixture = createPendingReservation("user-a@example.com", 1);
        Reservation reservation = reservationRepository.findById(fixture.reservationId()).orElseThrow();
        reservation.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        reservationRepository.save(reservation);

        mockMvc.perform(post("/api/reservations/{id}/payment", fixture.reservationId())
                        .with(user("user-a@example.com").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(successPaymentRequest()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Reservation has expired and cannot be paid."));

        org.assertj.core.api.Assertions.assertThat(reservationRepository.findById(fixture.reservationId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.EXPIRED);
        org.assertj.core.api.Assertions.assertThat(screeningSeatRepository.findById(fixture.screeningSeatIds().get(0)).orElseThrow().getStatus())
                .isEqualTo(ScreeningSeatStatus.AVAILABLE);
    }

    @Test
    void cancelledReservationCannotBePaid() throws Exception {
        ReservationFixture fixture = createPendingReservation("user-a@example.com", 1);
        mockMvc.perform(post("/api/reservations/{id}/cancel", fixture.reservationId())
                        .with(user("user-a@example.com").roles("USER")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/reservations/{id}/payment", fixture.reservationId())
                        .with(user("user-a@example.com").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(successPaymentRequest()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cancelled reservation cannot be paid."));
    }

    @Test
    void confirmedReservationCannotBePaidAgain() throws Exception {
        ReservationFixture fixture = createPendingReservation("user-a@example.com", 1);
        pay(fixture.reservationId(), "user-a@example.com", true);

        mockMvc.perform(post("/api/reservations/{id}/payment", fixture.reservationId())
                        .with(user("user-a@example.com").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(successPaymentRequest()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Reservation is already confirmed."));
    }

    @Test
    void concurrentSuccessfulPaymentsCannotBothSucceed() throws Exception {
        ReservationFixture fixture = createPendingReservation("user-a@example.com", 1);
        Callable<Boolean> first = () -> payThroughService(fixture.reservationId(), "user-a@example.com");
        Callable<Boolean> second = () -> payThroughService(fixture.reservationId(), "user-a@example.com");
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
            org.assertj.core.api.Assertions.assertThat(paymentRepository.findByReservationIdOrderByCreatedAtDesc(fixture.reservationId()).stream()
                    .filter(payment -> payment.getStatus() == PaymentStatus.SUCCESS)
                    .count()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void userCanSeeOwnPayments() throws Exception {
        ReservationFixture fixture = createPendingReservation("user-a@example.com", 1);
        pay(fixture.reservationId(), "user-a@example.com", false);

        mockMvc.perform(get("/api/reservations/{id}/payments", fixture.reservationId())
                        .with(user("user-a@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("FAILED"));
    }

    @Test
    void userCannotSeeAnotherUsersPayments() throws Exception {
        ReservationFixture fixture = createPendingReservation("user-a@example.com", 1);
        createUser("user-b@example.com", Role.USER);
        pay(fixture.reservationId(), "user-a@example.com", false);

        mockMvc.perform(get("/api/reservations/{id}/payments", fixture.reservationId())
                        .with(user("user-b@example.com").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void paymentReferenceIsUnique() throws Exception {
        ReservationFixture first = createPendingReservation("user-a@example.com", 1);
        ReservationFixture second = createPendingReservation("user-a@example.com", 1);

        pay(first.reservationId(), "user-a@example.com", false);
        pay(second.reservationId(), "user-a@example.com", false);

        List<String> references = paymentRepository.findAll().stream()
                .map(payment -> payment.getReference())
                .toList();
        org.assertj.core.api.Assertions.assertThat(references).doesNotHaveDuplicates();
    }

    @Test
    void adminCanSeePaymentOverview() throws Exception {
        ReservationFixture fixture = createPendingReservation("user-a@example.com", 1);
        pay(fixture.reservationId(), "user-a@example.com", true);

        mockMvc.perform(get("/api/admin/payments")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].userEmail").value("user-a@example.com"))
                .andExpect(jsonPath("$[0].movieTitle").value("Interstellar"));
    }

    private ReservationFixture createPendingReservation(String email, int seatCount) throws Exception {
        TestData data = createTestDataWithScreening();
        if (userRepository.findByEmail(email).isEmpty()) {
            createUser(email, Role.USER);
        }
        List<Long> seatIds = firstScreeningSeatIds(data.screeningId(), seatCount);
        for (Long seatId : seatIds) {
            holdSeat(data.screeningId(), seatId, email);
        }
        Long reservationId = createReservation(data.screeningId(), seatIds, email);
        return new ReservationFixture(data.screeningId(), reservationId, seatIds);
    }

    private void pay(Long reservationId, String email, boolean simulateSuccess) throws Exception {
        mockMvc.perform(post("/api/reservations/{id}/payment", reservationId)
                        .with(user(email).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(simulateSuccess ? successPaymentRequest() : failedPaymentRequest()))
                .andExpect(status().isOk());
    }

    private boolean payThroughService(Long reservationId, String email) {
        try {
            paymentService.create(
                    reservationId,
                    new PaymentRequest(PaymentMethod.CARD_SIMULATION, true),
                    authentication(email));
            return true;
        } catch (ConflictException exception) {
            return false;
        }
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
                        .content("""
                                {"screeningId":%d,"screeningSeatIds":%s}
                                """.formatted(screeningId, seatIds)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("reservationId").asLong();
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

    private String successPaymentRequest() {
        return "{\"method\":\"CARD_SIMULATION\",\"simulateSuccess\":true}";
    }

    private String failedPaymentRequest() {
        return "{\"method\":\"CARD_SIMULATION\",\"simulateSuccess\":false}";
    }

    private LocalDateTime futureStart(int hour, int minute) {
        return LocalDate.now().plusDays(2).atTime(hour, minute);
    }

    private record TestData(Long cityId, Long cinemaId, Long hallId, Long movieId, Long screeningId) {
    }

    private record ReservationFixture(Long screeningId, Long reservationId, List<Long> screeningSeatIds) {
    }
}
