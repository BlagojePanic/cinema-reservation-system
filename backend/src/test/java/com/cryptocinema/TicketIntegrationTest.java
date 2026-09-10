package com.cryptocinema;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.cryptocinema.dto.TicketValidationResponse;
import com.cryptocinema.entity.PaymentMethod;
import com.cryptocinema.entity.Reservation;
import com.cryptocinema.entity.ReservationStatus;
import com.cryptocinema.entity.Role;
import com.cryptocinema.entity.ScreeningSeat;
import com.cryptocinema.entity.TicketStatus;
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
import com.cryptocinema.service.CryptoTransactionVerification;
import com.cryptocinema.service.CryptoTransactionVerifier;
import com.cryptocinema.service.TicketService;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ticket-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.jwt.secret=12345678901234567890123456789012",
        "app.jwt.expiration-ms=3600000",
        "app.admin.email=",
        "app.admin.password=",
        "app.crypto.merchant-address=0x1111111111111111111111111111111111111111",
        "app.crypto.eth-rsd-rate=350000",
        "app.crypto.web3-rpc-url=",
        "app.crypto.network=Sepolia",
        "app.crypto.chain-id=11155111"
})
@AutoConfigureMockMvc
class TicketIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CryptoTransactionVerifier cryptoTransactionVerifier;

    @Autowired
    private TicketService ticketService;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private PaymentRepository paymentRepository;

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
    void successfulCardPaymentCreatesTicket() throws Exception {
        Long reservationId = createPendingReservation("user-a@example.com", 1).reservationId();

        payCard(reservationId, "user-a@example.com", true);

        org.assertj.core.api.Assertions.assertThat(ticketRepository.findByReservationId(reservationId)).isPresent();
    }

    @Test
    void successfulCryptoPaymentCreatesTicket() throws Exception {
        Fixture fixture = prepareCryptoPayment("user-a@example.com", 1);
        when(cryptoTransactionVerifier.verify(any(), any(), any(), any())).thenReturn(CryptoTransactionVerification.success());

        confirmCrypto(fixture, "0xticketcrypto");

        org.assertj.core.api.Assertions.assertThat(ticketRepository.findByReservationId(fixture.reservationId())).isPresent();
    }

    @Test
    void ticketIsNotCreatedBeforeSuccessfulPayment() throws Exception {
        Long reservationId = createPendingReservation("user-a@example.com", 1).reservationId();

        org.assertj.core.api.Assertions.assertThat(ticketRepository.existsByReservationId(reservationId)).isFalse();
        mockMvc.perform(get("/api/reservations/{id}/ticket", reservationId)
                        .with(user("user-a@example.com").roles("USER")))
                .andExpect(status().isConflict());
    }

    @Test
    void oneReservationCannotHaveMultipleTickets() throws Exception {
        Long reservationId = createPendingReservation("user-a@example.com", 1).reservationId();
        payCard(reservationId, "user-a@example.com", true);
        Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();

        ticketService.createTicketIfEligible(reservation);
        ticketService.createTicketIfEligible(reservation);

        org.assertj.core.api.Assertions.assertThat(ticketRepository.findAll()).hasSize(1);
    }

    @Test
    void ownerCanGetTicketAndQrCode() throws Exception {
        Long reservationId = createPendingReservation("user-a@example.com", 2).reservationId();
        payCard(reservationId, "user-a@example.com", true);

        mockMvc.perform(get("/api/reservations/{id}/ticket", reservationId)
                        .with(user("user-a@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movieTitle").value("Interstellar"))
                .andExpect(jsonPath("$.ticketStatus").value("VALID"))
                .andExpect(jsonPath("$.seats", hasSize(2)));

        MvcResult qrResult = mockMvc.perform(get("/api/reservations/{id}/ticket/qr", reservationId)
                        .with(user("user-a@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andReturn();

        org.assertj.core.api.Assertions.assertThat(qrResult.getResponse().getContentAsByteArray().length)
                .isGreaterThan(100);
    }

    @Test
    void anotherUserCannotGetTicket() throws Exception {
        Long reservationId = createPendingReservation("user-a@example.com", 1).reservationId();
        createUser("user-b@example.com", Role.USER);
        payCard(reservationId, "user-a@example.com", true);

        mockMvc.perform(get("/api/reservations/{id}/ticket", reservationId)
                        .with(user("user-b@example.com").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanInspectTicket() throws Exception {
        Long reservationId = createPaidReservation("user-a@example.com");
        String ticketCode = ticketCodeForReservation(reservationId);

        mockMvc.perform(get("/api/admin/tickets/{ticketCode}", ticketCode)
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketCode").value(ticketCode))
                .andExpect(jsonPath("$.userEmail").value("user-a@example.com"))
                .andExpect(jsonPath("$.reservationStatus").value("CONFIRMED"));
    }

    @Test
    void validTicketCanBeValidatedAndBecomesUsed() throws Exception {
        Long reservationId = createPaidReservation("user-a@example.com");
        String ticketCode = ticketCodeForReservation(reservationId);

        mockMvc.perform(post("/api/admin/tickets/{ticketCode}/validate", ticketCode)
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.ticket.ticketStatus").value("USED"));

        org.assertj.core.api.Assertions.assertThat(ticketRepository.findByTicketCode(ticketCode).orElseThrow().getStatus())
                .isEqualTo(TicketStatus.USED);
    }

    @Test
    void usedTicketCannotBeValidatedAgain() throws Exception {
        Long reservationId = createPaidReservation("user-a@example.com");
        String ticketCode = ticketCodeForReservation(reservationId);

        validateTicket(ticketCode);

        mockMvc.perform(post("/api/admin/tickets/{ticketCode}/validate", ticketCode)
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(false))
                .andExpect(jsonPath("$.message").value("Ticket is already used."))
                .andExpect(jsonPath("$.ticket.usedAt").isNotEmpty());
    }

    @Test
    void invalidTicketCodeIsRejected() throws Exception {
        mockMvc.perform(get("/api/admin/tickets/{ticketCode}", "missing-code")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/admin/tickets/{ticketCode}/validate", "missing-code")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void ticketCannotBeValidatedWhenReservationIsNotConfirmed() throws Exception {
        Long reservationId = createPaidReservation("user-a@example.com");
        String ticketCode = ticketCodeForReservation(reservationId);
        Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
        reservation.setStatus(ReservationStatus.CANCELLED);
        reservationRepository.save(reservation);

        mockMvc.perform(post("/api/admin/tickets/{ticketCode}/validate", ticketCode)
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Reservation is not confirmed."));
    }

    @Test
    void concurrentValidationCannotBothSucceed() throws Exception {
        Long reservationId = createPaidReservation("user-a@example.com");
        String ticketCode = ticketCodeForReservation(reservationId);
        Callable<Boolean> first = () -> validateThroughService(ticketCode);
        Callable<Boolean> second = () -> validateThroughService(ticketCode);
        var executor = Executors.newFixedThreadPool(2);
        try {
            long acceptedCount = executor.invokeAll(List.of(first, second)).stream()
                    .filter(result -> {
                        try {
                            return result.get();
                        } catch (Exception exception) {
                            throw new RuntimeException(exception);
                        }
                    })
                    .count();

            org.assertj.core.api.Assertions.assertThat(acceptedCount).isEqualTo(1);
            org.assertj.core.api.Assertions.assertThat(ticketRepository.findByTicketCode(ticketCode).orElseThrow().getStatus())
                    .isEqualTo(TicketStatus.USED);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void successfulPaymentsStillBlockDuplicatePaymentAttempts() throws Exception {
        Long cardReservationId = createPaidReservation("user-a@example.com");

        mockMvc.perform(post("/api/reservations/{id}/payment", cardReservationId)
                        .with(user("user-a@example.com").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cardRequest(true)))
                .andExpect(status().isConflict());

        Fixture cryptoFixture = prepareCryptoPayment("user-a@example.com", 1);
        when(cryptoTransactionVerifier.verify(any(), any(), any(), any())).thenReturn(CryptoTransactionVerification.success());
        confirmCrypto(cryptoFixture, "0xduplicatepayment");

        mockMvc.perform(post("/api/reservations/{id}/crypto-payment/confirm", cryptoFixture.reservationId())
                        .with(user("user-a@example.com").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmRequest(cryptoFixture.paymentId(), "0xduplicatepayment")))
                .andExpect(status().isConflict());
    }

    private Long createPaidReservation(String email) throws Exception {
        Long reservationId = createPendingReservation(email, 1).reservationId();
        payCard(reservationId, email, true);
        return reservationId;
    }

    private void payCard(Long reservationId, String email, boolean simulateSuccess) throws Exception {
        mockMvc.perform(post("/api/reservations/{id}/payment", reservationId)
                        .with(user(email).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cardRequest(simulateSuccess)))
                .andExpect(status().isOk());
    }

    private Fixture prepareCryptoPayment(String email, int seatCount) throws Exception {
        ReservationFixture reservation = createPendingReservation(email, seatCount);
        MvcResult result = mockMvc.perform(post("/api/reservations/{id}/crypto-payment/prepare", reservation.reservationId())
                        .with(user(email).roles("USER")))
                .andExpect(status().isOk())
                .andReturn();
        Long paymentId = objectMapper.readTree(result.getResponse().getContentAsString()).get("paymentId").asLong();
        return new Fixture(reservation.reservationId(), paymentId);
    }

    private void confirmCrypto(Fixture fixture, String transactionHash) throws Exception {
        mockMvc.perform(post("/api/reservations/{id}/crypto-payment/confirm", fixture.reservationId())
                        .with(user("user-a@example.com").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmRequest(fixture.paymentId(), transactionHash)))
                .andExpect(status().isOk());
    }

    private void validateTicket(String ticketCode) throws Exception {
        mockMvc.perform(post("/api/admin/tickets/{ticketCode}/validate", ticketCode)
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    private boolean validateThroughService(String ticketCode) {
        try {
            TicketValidationResponse response = ticketService.validate(ticketCode);
            return response.accepted();
        } catch (ConflictException exception) {
            return false;
        }
    }

    private String ticketCodeForReservation(Long reservationId) {
        return ticketRepository.findByReservationId(reservationId).orElseThrow().getTicketCode();
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
        return new ReservationFixture(reservationId);
    }

    private TestData createTestDataWithScreening() throws Exception {
        Long cityId = createCity();
        Long cinemaId = createCinema(cityId);
        Long hallId = createHall(cinemaId);
        Long movieId = createMovie();
        generateSeats(hallId);
        Long screeningId = createScreening(movieId, hallId, futureStart(20, 30));
        return new TestData(screeningId);
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
                        .content("{\"name\":\"Arena Cineplex\",\"address\":\"Bulevar Mihajla Pupina 3\",\"cityId\":%d}".formatted(cityId)))
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
                        .content("{\"movieId\":%d,\"hallId\":%d,\"startTime\":\"%s\",\"ticketPrice\":650}".formatted(movieId, hallId, startTime)))
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
                        .content("{\"screeningId\":%d,\"screeningSeatIds\":%s}".formatted(screeningId, seatIds)))
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

    private String cardRequest(boolean simulateSuccess) {
        return """
                {"method":"%s","simulateSuccess":%s}
                """.formatted(PaymentMethod.CARD_SIMULATION, simulateSuccess);
    }

    private String confirmRequest(Long paymentId, String transactionHash) {
        return """
                {"paymentId":%d,"transactionHash":"%s","walletAddress":"0x2222222222222222222222222222222222222222"}
                """.formatted(paymentId, transactionHash);
    }

    private LocalDateTime futureStart(int hour, int minute) {
        return LocalDate.now().plusDays(2).atTime(hour, minute);
    }

    private record TestData(Long screeningId) {
    }

    private record ReservationFixture(Long reservationId) {
    }

    private record Fixture(Long reservationId, Long paymentId) {
    }
}
