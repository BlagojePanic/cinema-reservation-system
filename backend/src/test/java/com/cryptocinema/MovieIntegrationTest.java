package com.cryptocinema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockMultipartFile;

import com.cryptocinema.entity.Cinema;
import com.cryptocinema.entity.City;
import com.cryptocinema.entity.Hall;
import com.cryptocinema.entity.MovieStatus;
import com.cryptocinema.entity.Payment;
import com.cryptocinema.entity.PaymentMethod;
import com.cryptocinema.entity.PaymentStatus;
import com.cryptocinema.entity.Reservation;
import com.cryptocinema.entity.ReservationStatus;
import com.cryptocinema.entity.Role;
import com.cryptocinema.entity.Screening;
import com.cryptocinema.entity.Seat;
import com.cryptocinema.entity.Ticket;
import com.cryptocinema.entity.TicketStatus;
import com.cryptocinema.entity.User;
import com.cryptocinema.repository.CinemaRepository;
import com.cryptocinema.repository.CityRepository;
import com.cryptocinema.repository.HallRepository;
import com.cryptocinema.repository.MovieRepository;
import com.cryptocinema.repository.PaymentRepository;
import com.cryptocinema.repository.ReservationRepository;
import com.cryptocinema.repository.ScreeningRepository;
import com.cryptocinema.repository.ScreeningSeatRepository;
import com.cryptocinema.repository.SeatRepository;
import com.cryptocinema.repository.TicketRepository;
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
        "app.admin.password=",
        "app.uploads.root=target/test-uploads/movie-test",
        "app.uploads.max-poster-bytes=20",
        "app.uploads.max-trailer-bytes=30"
})
@AutoConfigureMockMvc
class MovieIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MovieRepository movieRepository;

    @Autowired
    private ScreeningRepository screeningRepository;

    @Autowired
    private ScreeningSeatRepository screeningSeatRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private CityRepository cityRepository;

    @Autowired
    private CinemaRepository cinemaRepository;

    @Autowired
    private HallRepository hallRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        ticketRepository.deleteAll();
        paymentRepository.deleteAll();
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
    void movieWithOnlyPastScreeningsCanBeArchivedAndIsRemovedFromPublicCatalogue() throws Exception {
        Long movieId = createMovie("Interstellar");
        createScreening(movieId, LocalDateTime.now().minusDays(2));

        mockMvc.perform(delete("/api/admin/movies/{id}", movieId)
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isNoContent());

        assertThat(movieRepository.findById(movieId)).isPresent();
        assertThat(movieRepository.findById(movieId).orElseThrow().getStatus()).isEqualTo(MovieStatus.ARCHIVED);

        mockMvc.perform(get("/api/movies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/admin/movies")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ARCHIVED"));
    }

    @Test
    void movieWithFutureScreeningCannotBeArchivedOrDeleted() throws Exception {
        Long movieId = createMovie("Interstellar");
        createScreening(movieId, LocalDateTime.now().plusDays(2));

        mockMvc.perform(delete("/api/admin/movies/{id}", movieId)
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Movie cannot be archived while future screenings exist."));

        assertThat(movieRepository.findById(movieId)).isPresent();
        assertThat(movieRepository.findById(movieId).orElseThrow().getStatus()).isEqualTo(MovieStatus.ACTIVE);
    }

    @Test
    void historicalReservationPaymentAndTicketRemainAfterMovieArchive() throws Exception {
        Long movieId = createMovie("Interstellar");
        Screening screening = createScreening(movieId, LocalDateTime.now().minusDays(2));
        User user = createRegularUser();
        Reservation reservation = createReservation(user, screening);
        Payment payment = createPayment(user, reservation);
        Ticket ticket = createTicket(reservation);

        mockMvc.perform(delete("/api/admin/movies/{id}", movieId)
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isNoContent());

        assertThat(screeningRepository.findById(screening.getId())).isPresent();
        assertThat(reservationRepository.findById(reservation.getId())).isPresent();
        assertThat(paymentRepository.findById(payment.getId())).isPresent();
        assertThat(ticketRepository.findById(ticket.getId())).isPresent();
    }

    @Test
    void archivedMovieCannotReceiveNewFutureScreeningUnlessRestored() throws Exception {
        Long movieId = createMovie("Interstellar");
        Screening pastScreening = createScreening(movieId, LocalDateTime.now().minusDays(2));
        Hall hall = pastScreening.getHall();

        mockMvc.perform(delete("/api/admin/movies/{id}", movieId)
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/admin/screenings")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(screeningRequest(movieId, hall.getId(), LocalDateTime.now().plusDays(2))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Archived movie cannot receive new screenings."));

        mockMvc.perform(post("/api/admin/movies/{id}/restore", movieId)
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(post("/api/admin/screenings")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(screeningRequest(movieId, hall.getId(), LocalDateTime.now().plusDays(2))))
                .andExpect(status().isCreated());
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

    @Test
    void adminCanUploadValidPosterImage() throws Exception {
        Long movieId = createMovie("Interstellar");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "poster.png",
                "image/png",
                new byte[] {1, 2, 3, 4, 5});

        mockMvc.perform(multipart("/api/admin/movies/{id}/poster", movieId)
                        .file(file)
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posterUrl").value(org.hamcrest.Matchers.startsWith("/api/media/posters/")));
    }

    @Test
    void userCannotUploadPoster() throws Exception {
        Long movieId = createMovie("Interstellar");
        MockMultipartFile file = new MockMultipartFile("file", "poster.png", "image/png", new byte[] {1, 2, 3});

        mockMvc.perform(multipart("/api/admin/movies/{id}/poster", movieId)
                        .file(file)
                        .with(user("user@example.com").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidPosterFileTypeIsRejected() throws Exception {
        Long movieId = createMovie("Interstellar");
        MockMultipartFile file = new MockMultipartFile("file", "poster.txt", "text/plain", new byte[] {1, 2, 3});

        mockMvc.perform(multipart("/api/admin/movies/{id}/poster", movieId)
                        .file(file)
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Unsupported upload file type."));
    }

    @Test
    void oversizedTrailerFileIsRejected() throws Exception {
        Long movieId = createMovie("Interstellar");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "trailer.mp4",
                "video/mp4",
                new byte[31]);

        mockMvc.perform(multipart("/api/admin/movies/{id}/trailer", movieId)
                        .file(file)
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Upload file is too large."));
    }

    @Test
    void uploadedFileIsServedSuccessfully() throws Exception {
        Long movieId = createMovie("Interstellar");
        byte[] bytes = new byte[] {9, 8, 7};
        MockMultipartFile file = new MockMultipartFile("file", "poster.webp", "image/webp", bytes);

        MvcResult upload = mockMvc.perform(multipart("/api/admin/movies/{id}/poster", movieId)
                        .file(file)
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn();
        String posterUrl = objectMapper.readTree(upload.getResponse().getContentAsString()).get("posterUrl").asText();

        mockMvc.perform(get(posterUrl))
                .andExpect(status().isOk())
                .andExpect(content().bytes(bytes));
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

    private Screening createScreening(Long movieId, LocalDateTime startTime) {
        City city = new City();
        city.setName("Belgrade");
        City savedCity = cityRepository.save(city);

        Cinema cinema = new Cinema();
        cinema.setName("CryptoCinema Center");
        cinema.setAddress("Main 1");
        cinema.setCity(savedCity);
        Cinema savedCinema = cinemaRepository.save(cinema);

        Hall hall = new Hall();
        hall.setName("Hall 1");
        hall.setCinema(savedCinema);
        Hall savedHall = hallRepository.save(hall);

        Seat seat = new Seat();
        seat.setHall(savedHall);
        seat.setRowLabel("A");
        seat.setSeatNumber(1);
        seatRepository.save(seat);

        Screening screening = new Screening();
        screening.setMovie(movieRepository.findById(movieId).orElseThrow());
        screening.setHall(savedHall);
        screening.setStartTime(startTime);
        screening.setTicketPrice(new BigDecimal("650.00"));
        return screeningRepository.save(screening);
    }

    private User createRegularUser() {
        User user = new User();
        user.setFirstName("Test");
        user.setLastName("User");
        user.setEmail("user@example.com");
        user.setPassword("encoded");
        user.setRole(Role.USER);
        return userRepository.save(user);
    }

    private Reservation createReservation(User user, Screening screening) {
        LocalDateTime now = LocalDateTime.now();
        Reservation reservation = new Reservation();
        reservation.setUser(user);
        reservation.setScreening(screening);
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservation.setTotalAmount(new BigDecimal("650.00"));
        reservation.setCreatedAt(now.minusDays(2));
        reservation.setUpdatedAt(now.minusDays(2));
        reservation.setExpiresAt(now.minusDays(2).plusMinutes(10));
        return reservationRepository.save(reservation);
    }

    private Payment createPayment(User user, Reservation reservation) {
        Payment payment = new Payment();
        payment.setUser(user);
        payment.setReservation(reservation);
        payment.setAmount(new BigDecimal("650.00"));
        payment.setCurrency("RSD");
        payment.setMethod(PaymentMethod.CARD_SIMULATION);
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setCreatedAt(LocalDateTime.now().minusDays(2));
        payment.setCompletedAt(LocalDateTime.now().minusDays(2));
        payment.setReference("PAY-LIFECYCLE-1");
        return paymentRepository.save(payment);
    }

    private Ticket createTicket(Reservation reservation) {
        Ticket ticket = new Ticket();
        ticket.setReservation(reservation);
        ticket.setTicketCode("TICKET-LIFECYCLE-1");
        ticket.setStatus(TicketStatus.VALID);
        ticket.setCreatedAt(LocalDateTime.now().minusDays(2));
        return ticketRepository.save(ticket);
    }

    private String screeningRequest(Long movieId, Long hallId, LocalDateTime startTime) {
        return """
                {
                  "movieId": %d,
                  "hallId": %d,
                  "startTime": "%s",
                  "ticketPrice": 650
                }
                """.formatted(movieId, hallId, startTime);
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
