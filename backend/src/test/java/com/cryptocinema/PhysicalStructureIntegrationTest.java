package com.cryptocinema;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import com.cryptocinema.repository.CinemaRepository;
import com.cryptocinema.repository.CityRepository;
import com.cryptocinema.repository.HallRepository;
import com.cryptocinema.repository.SeatRepository;
import com.cryptocinema.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:physical-structure-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
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
class PhysicalStructureIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private HallRepository hallRepository;

    @Autowired
    private CinemaRepository cinemaRepository;

    @Autowired
    private CityRepository cityRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        seatRepository.deleteAll();
        hallRepository.deleteAll();
        cinemaRepository.deleteAll();
        cityRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void adminCanCreateCity() throws Exception {
        mockMvc.perform(post("/api/admin/cities")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Novi Sad"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name").value("Novi Sad"));
    }

    @Test
    void userCannotCreateCity() throws Exception {
        mockMvc.perform(post("/api/admin/cities")
                        .with(user("user@example.com").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Novi Sad"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void guestCanGetCities() throws Exception {
        createCity("Novi Sad");

        mockMvc.perform(get("/api/cities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Novi Sad"));
    }

    @Test
    void adminCanCreateCinemaInValidCity() throws Exception {
        Long cityId = createCity("Novi Sad");

        mockMvc.perform(post("/api/admin/cinemas")
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
                .andExpect(jsonPath("$.name").value("Arena Cineplex"))
                .andExpect(jsonPath("$.city.id").value(cityId));
    }

    @Test
    void cannotCreateCinemaForMissingCity() throws Exception {
        mockMvc.perform(post("/api/admin/cinemas")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Arena Cineplex",
                                  "address": "Bulevar Mihajla Pupina 3",
                                  "cityId": 9999
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("City not found"));
    }

    @Test
    void adminCanCreateHall() throws Exception {
        Long cinemaId = createCinema();

        mockMvc.perform(post("/api/admin/halls")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Sala 1",
                                  "cinemaId": %d
                                }
                                """.formatted(cinemaId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Sala 1"))
                .andExpect(jsonPath("$.cinemaId").value(cinemaId));
    }

    @Test
    void bulkGenerateSeatsCreatesExpectedNumberOfSeats() throws Exception {
        Long hallId = createHall();

        mockMvc.perform(post("/api/admin/halls/{hallId}/seats/generate", hallId)
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rows": 2,
                                  "seatsPerRow": 3
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[0].rowLabel").value("A"))
                .andExpect(jsonPath("$[0].seatNumber").value(1))
                .andExpect(jsonPath("$[5].rowLabel").value("B"))
                .andExpect(jsonPath("$[5].seatNumber").value(3));
    }

    @Test
    void duplicateSeatsCannotExistInSameHall() throws Exception {
        Long hallId = createHall();
        createSeat(hallId, "A", 1);

        mockMvc.perform(post("/api/admin/seats")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rowLabel": "a",
                                  "seatNumber": 1,
                                  "hallId": %d
                                }
                                """.formatted(hallId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Seat already exists in this hall"));
    }

    @Test
    void guestCanPubliclyBrowseHallsAndSeats() throws Exception {
        Long hallId = createHall();
        Long cinemaId = hallRepository.findById(hallId).orElseThrow().getCinema().getId();
        createSeat(hallId, "A", 1);

        mockMvc.perform(get("/api/cinemas/{cinemaId}/halls", cinemaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(hallId));

        mockMvc.perform(get("/api/halls/{hallId}/seats", hallId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].rowLabel").value("A"))
                .andExpect(jsonPath("$[0].seatNumber").value(1));
    }

    @Test
    void deletingParentWithChildrenIsRejected() throws Exception {
        Long cityId = createCity("Novi Sad");
        createCinema(cityId);

        mockMvc.perform(delete("/api/admin/cities/{id}", cityId)
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("City cannot be deleted while it has cinemas"));
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

    private Long createCinema() throws Exception {
        Long cityId = createCity("Novi Sad");
        return createCinema(cityId);
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

    private Long createHall() throws Exception {
        Long cinemaId = createCinema();
        MvcResult result = mockMvc.perform(post("/api/admin/halls")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Sala 1",
                                  "cinemaId": %d
                                }
                                """.formatted(cinemaId)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private void createSeat(Long hallId, String rowLabel, int seatNumber) throws Exception {
        mockMvc.perform(post("/api/admin/seats")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rowLabel": "%s",
                                  "seatNumber": %d,
                                  "hallId": %d
                                }
                                """.formatted(rowLabel, seatNumber, hallId)))
                .andExpect(status().isCreated());
    }
}
