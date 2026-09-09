package com.cryptocinema;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.cryptocinema.entity.PaymentMethod;
import com.cryptocinema.entity.PaymentStatus;
import com.cryptocinema.entity.Reservation;
import com.cryptocinema.entity.ReservationStatus;
import com.cryptocinema.entity.Role;
import com.cryptocinema.entity.ScreeningSeat;
import com.cryptocinema.entity.ScreeningSeatStatus;
import com.cryptocinema.entity.User;
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
import com.cryptocinema.repository.UserRepository;
import com.cryptocinema.service.CryptoTransactionVerification;
import com.cryptocinema.service.CryptoTransactionVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:crypto-payment-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
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
class CryptoPaymentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CryptoTransactionVerifier cryptoTransactionVerifier;

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
    void userCanPrepareCryptoPaymentForOwnPendingReservation() throws Exception {
        Long reservationId = createPendingReservation("user-a@example.com", 1).reservationId();

        mockMvc.perform(post("/api/reservations/{id}/crypto-payment/prepare", reservationId)
                        .with(user("user-a@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationId").value(reservationId))
                .andExpect(jsonPath("$.network").value("Sepolia"))
                .andExpect(jsonPath("$.chainId").value(11155111));
    }

    @Test
    void guestCannotPrepareCryptoPayment() throws Exception {
        Long reservationId = createPendingReservation("user-a@example.com", 1).reservationId();

        mockMvc.perform(post("/api/reservations/{id}/crypto-payment/prepare", reservationId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userCannotPrepareCryptoPaymentForAnotherUsersReservation() throws Exception {
        Long reservationId = createPendingReservation("user-a@example.com", 1).reservationId();
        createUser("user-b@example.com", Role.USER);

        mockMvc.perform(post("/api/reservations/{id}/crypto-payment/prepare", reservationId)
                        .with(user("user-b@example.com").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void prepareCalculatesCryptoAmountFromReservationTotalAndExchangeRate() throws Exception {
        Long reservationId = createPendingReservation("user-a@example.com", 2).reservationId();

        mockMvc.perform(post("/api/reservations/{id}/crypto-payment/prepare", reservationId)
                        .with(user("user-a@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountRsd").value(1300))
                .andExpect(jsonPath("$.cryptoAmount").value(0.003714285714285714))
                .andExpect(jsonPath("$.cryptoCurrency").value("ETH"));
    }

    @Test
    void merchantAddressComesFromConfigurationAndPrepareCreatesPendingCryptoPayment() throws Exception {
        Long reservationId = createPendingReservation("user-a@example.com", 1).reservationId();

        mockMvc.perform(post("/api/reservations/{id}/crypto-payment/prepare", reservationId)
                        .with(user("user-a@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantAddress").value("0x1111111111111111111111111111111111111111"));

        org.assertj.core.api.Assertions.assertThat(paymentRepository.findAll())
                .hasSize(1)
                .allSatisfy(payment -> {
                    org.assertj.core.api.Assertions.assertThat(payment.getMethod()).isEqualTo(PaymentMethod.CRYPTO);
                    org.assertj.core.api.Assertions.assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
                });
    }

    @Test
    void validVerifiedTransactionConfirmsCryptoPaymentAndReservation() throws Exception {
        Fixture fixture = prepareCryptoPayment("user-a@example.com", 1);
        when(cryptoTransactionVerifier.verify(any(), any(), any(), any())).thenReturn(CryptoTransactionVerification.success());

        mockMvc.perform(post("/api/reservations/{id}/crypto-payment/confirm", fixture.reservationId())
                        .with(user("user-a@example.com").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmRequest(fixture.paymentId(), "0xaaa")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.transactionHash").value("0xaaa"));

        org.assertj.core.api.Assertions.assertThat(reservationRepository.findById(fixture.reservationId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.CONFIRMED);
        org.assertj.core.api.Assertions.assertThat(screeningSeatRepository.findById(fixture.screeningSeatIds().get(0)).orElseThrow().getStatus())
                .isEqualTo(ScreeningSeatStatus.RESERVED);
    }

    @Test
    void invalidRecipientMarksPaymentFailedAndDoesNotConfirmReservation() throws Exception {
        Fixture fixture = prepareCryptoPayment("user-a@example.com", 1);
        when(cryptoTransactionVerifier.verify(any(), any(), any(), any()))
                .thenReturn(CryptoTransactionVerification.failed("Transaction recipient does not match merchant wallet."));

        mockMvc.perform(post("/api/reservations/{id}/crypto-payment/confirm", fixture.reservationId())
                        .with(user("user-a@example.com").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmRequest(fixture.paymentId(), "0xbbb")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Transaction recipient does not match merchant wallet."));

        assertFailedPaymentAndPendingReservation(fixture);
    }

    @Test
    void wrongTransactionValueMarksPaymentFailed() throws Exception {
        Fixture fixture = prepareCryptoPayment("user-a@example.com", 1);
        when(cryptoTransactionVerifier.verify(any(), any(), any(), any()))
                .thenReturn(CryptoTransactionVerification.failed("Transaction value is lower than expected amount."));

        mockMvc.perform(post("/api/reservations/{id}/crypto-payment/confirm", fixture.reservationId())
                        .with(user("user-a@example.com").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmRequest(fixture.paymentId(), "0xccc")))
                .andExpect(status().isConflict());

        assertFailedPaymentAndPendingReservation(fixture);
    }

    @Test
    void failedReceiptMarksPaymentFailed() throws Exception {
        Fixture fixture = prepareCryptoPayment("user-a@example.com", 1);
        when(cryptoTransactionVerifier.verify(any(), any(), any(), any()))
                .thenReturn(CryptoTransactionVerification.failed("Transaction receipt status is failed."));

        mockMvc.perform(post("/api/reservations/{id}/crypto-payment/confirm", fixture.reservationId())
                        .with(user("user-a@example.com").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmRequest(fixture.paymentId(), "0xddd")))
                .andExpect(status().isConflict());

        assertFailedPaymentAndPendingReservation(fixture);
    }

    @Test
    void pendingTransactionDoesNotConfirmReservation() throws Exception {
        Fixture fixture = prepareCryptoPayment("user-a@example.com", 1);
        when(cryptoTransactionVerifier.verify(any(), any(), any(), any()))
                .thenReturn(CryptoTransactionVerification.pending("Transaction is waiting for confirmation."));

        mockMvc.perform(post("/api/reservations/{id}/crypto-payment/confirm", fixture.reservationId())
                        .with(user("user-a@example.com").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmRequest(fixture.paymentId(), "0xeee")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));

        org.assertj.core.api.Assertions.assertThat(reservationRepository.findById(fixture.reservationId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.PENDING_PAYMENT);
    }

    @Test
    void reusedTransactionHashIsRejected() throws Exception {
        Fixture first = prepareCryptoPayment("user-a@example.com", 1);
        Fixture second = prepareCryptoPayment("user-a@example.com", 1);
        when(cryptoTransactionVerifier.verify(any(), any(), any(), any())).thenReturn(CryptoTransactionVerification.success());
        confirm(first, "0xreuse");

        mockMvc.perform(post("/api/reservations/{id}/crypto-payment/confirm", second.reservationId())
                        .with(user("user-a@example.com").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmRequest(second.paymentId(), "0xreuse")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Transaction hash is already used."));
    }

    @Test
    void expiredReservationCannotBecomeConfirmedByCrypto() throws Exception {
        Fixture fixture = prepareCryptoPayment("user-a@example.com", 1);
        Reservation reservation = reservationRepository.findById(fixture.reservationId()).orElseThrow();
        reservation.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        reservationRepository.save(reservation);

        mockMvc.perform(post("/api/reservations/{id}/crypto-payment/confirm", fixture.reservationId())
                        .with(user("user-a@example.com").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmRequest(fixture.paymentId(), "0xexpired")))
                .andExpect(status().isConflict());

        org.assertj.core.api.Assertions.assertThat(reservationRepository.findById(fixture.reservationId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.EXPIRED);
    }

    @Test
    void successfulCardPaymentBlocksCryptoPayment() throws Exception {
        Long reservationId = createPendingReservation("user-a@example.com", 1).reservationId();
        mockMvc.perform(post("/api/reservations/{id}/payment", reservationId)
                        .with(user("user-a@example.com").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"CARD_SIMULATION\",\"simulateSuccess\":true}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/reservations/{id}/crypto-payment/prepare", reservationId)
                        .with(user("user-a@example.com").roles("USER")))
                .andExpect(status().isConflict());
    }

    @Test
    void successfulCryptoPaymentBlocksCardPayment() throws Exception {
        Fixture fixture = prepareCryptoPayment("user-a@example.com", 1);
        when(cryptoTransactionVerifier.verify(any(), any(), any(), any())).thenReturn(CryptoTransactionVerification.success());
        confirm(fixture, "0xcryptosuccess");

        mockMvc.perform(post("/api/reservations/{id}/payment", fixture.reservationId())
                        .with(user("user-a@example.com").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"CARD_SIMULATION\",\"simulateSuccess\":true}"))
                .andExpect(status().isConflict());
    }

    @Test
    void userCannotConfirmAnotherUsersPayment() throws Exception {
        Fixture fixture = prepareCryptoPayment("user-a@example.com", 1);
        createUser("user-b@example.com", Role.USER);

        mockMvc.perform(post("/api/reservations/{id}/crypto-payment/confirm", fixture.reservationId())
                        .with(user("user-b@example.com").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmRequest(fixture.paymentId(), "0xforeign")))
                .andExpect(status().isForbidden());
    }

    @Test
    void transactionHashIsUniqueWhenPresent() throws Exception {
        Fixture first = prepareCryptoPayment("user-a@example.com", 1);
        Fixture second = prepareCryptoPayment("user-a@example.com", 1);
        when(cryptoTransactionVerifier.verify(any(), any(), any(), any())).thenReturn(CryptoTransactionVerification.success());
        confirm(first, "0xunique");

        org.assertj.core.api.Assertions.assertThat(paymentRepository.existsByTransactionHash("0xunique")).isTrue();
        mockMvc.perform(post("/api/reservations/{id}/crypto-payment/confirm", second.reservationId())
                        .with(user("user-a@example.com").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmRequest(second.paymentId(), "0xunique")))
                .andExpect(status().isConflict());
    }

    @Test
    void adminCanSeeAllReservationsWithLatestPayment() throws Exception {
        Fixture fixture = prepareCryptoPayment("user-a@example.com", 1);

        mockMvc.perform(get("/api/admin/reservations")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].reservationId").value(fixture.reservationId()))
                .andExpect(jsonPath("$[0].userEmail").value("user-a@example.com"))
                .andExpect(jsonPath("$[0].latestPaymentStatus").value("PENDING"))
                .andExpect(jsonPath("$[0].latestPaymentMethod").value("CRYPTO"));
    }

    @Test
    void adminPaymentOverviewIncludesCryptoFields() throws Exception {
        Fixture fixture = prepareCryptoPayment("user-a@example.com", 1);

        mockMvc.perform(get("/api/admin/payments")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].method").value("CRYPTO"))
                .andExpect(jsonPath("$[0].cryptoCurrency").value("ETH"))
                .andExpect(jsonPath("$[0].network").value("Sepolia"))
                .andExpect(jsonPath("$[0].reservationId").value(fixture.reservationId()));
    }

    private void assertFailedPaymentAndPendingReservation(Fixture fixture) {
        org.assertj.core.api.Assertions.assertThat(paymentRepository.findById(fixture.paymentId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.FAILED);
        org.assertj.core.api.Assertions.assertThat(reservationRepository.findById(fixture.reservationId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.PENDING_PAYMENT);
    }

    private Fixture prepareCryptoPayment(String email, int seatCount) throws Exception {
        ReservationFixture reservation = createPendingReservation(email, seatCount);
        MvcResult result = mockMvc.perform(post("/api/reservations/{id}/crypto-payment/prepare", reservation.reservationId())
                        .with(user(email).roles("USER")))
                .andExpect(status().isOk())
                .andReturn();
        Long paymentId = objectMapper.readTree(result.getResponse().getContentAsString()).get("paymentId").asLong();
        return new Fixture(reservation.reservationId(), paymentId, reservation.screeningSeatIds());
    }

    private void confirm(Fixture fixture, String transactionHash) throws Exception {
        mockMvc.perform(post("/api/reservations/{id}/crypto-payment/confirm", fixture.reservationId())
                        .with(user("user-a@example.com").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmRequest(fixture.paymentId(), transactionHash)))
                .andExpect(status().isOk());
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
        return new ReservationFixture(reservationId, seatIds);
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

    private record ReservationFixture(Long reservationId, List<Long> screeningSeatIds) {
    }

    private record Fixture(Long reservationId, Long paymentId, List<Long> screeningSeatIds) {
    }
}
