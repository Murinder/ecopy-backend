package com.example.portfolioservice.integration;

import com.example.portfolioservice.dto.CreateAchievementDto;
import com.example.portfolioservice.dto.CreateSkillDto;
import com.example.portfolioservice.model.Achievement;
import com.example.portfolioservice.model.Portfolio;
import com.example.portfolioservice.model.PortfolioSkill;
import com.example.portfolioservice.repository.AchievementRepository;
import com.example.portfolioservice.repository.PortfolioRepository;
import com.example.portfolioservice.repository.PortfolioSkillRepository;
import com.example.portfolioservice.repository.ReviewRepository;
import com.example.testsupport.BaseIntegrationTest;
import com.example.testsupport.security.TestSecurityHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class PortfolioControllerIT extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private AchievementRepository achievementRepository;

    @Autowired
    private PortfolioSkillRepository skillRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ADMIN_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        reviewRepository.deleteAll();
        skillRepository.deleteAll();
        achievementRepository.deleteAll();
        portfolioRepository.deleteAll();
    }

    // --- Portfolio ---

    @Test
    void getPortfolio_happyPath_returnsPortfolio() throws Exception {
        Portfolio portfolio = createPortfolioInDb(USER_ID);

        mockMvc.perform(TestSecurityHelper.withStudent(
                        get("/api/v1/portfolios/{userId}", USER_ID), USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(USER_ID.toString()));
    }

    @Test
    void getPortfolio_notFound_returns404() throws Exception {
        UUID unknownId = UUID.randomUUID();

        mockMvc.perform(TestSecurityHelper.withStudent(
                        get("/api/v1/portfolios/{userId}", unknownId), USER_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void getPortfolio_unauthenticated_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/portfolios/{userId}", USER_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateVisibility_happyPath_updatesAndReturns() throws Exception {
        createPortfolioInDb(USER_ID);
        String newSettings = "{\"showSkills\": true}";

        mockMvc.perform(TestSecurityHelper.withStudent(
                        put("/api/v1/portfolios/{userId}/visibility", USER_ID), USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newSettings))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visibilitySettings").value(newSettings));

        Portfolio updated = portfolioRepository.findById(USER_ID).orElseThrow();
        assertThat(updated.getVisibilitySettings()).isEqualTo(newSettings);
    }

    // --- Achievements ---

    @Test
    void addAchievement_happyPath_createsAndReturns201() throws Exception {
        createPortfolioInDb(USER_ID);

        CreateAchievementDto dto = new CreateAchievementDto();
        dto.setType(Achievement.AchievementType.CERTIFICATE);
        dto.setTitle("Java OCA");
        dto.setDescription("Oracle certification");
        dto.setDate(LocalDate.of(2025, 6, 15));
        dto.setIsExternal(true);
        dto.setIssuer("Oracle");

        mockMvc.perform(TestSecurityHelper.withStudent(
                        post("/api/v1/portfolios/{userId}/achievements", USER_ID), USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Java OCA"))
                .andExpect(jsonPath("$.type").value("CERTIFICATE"));

        assertThat(achievementRepository.findByPortfolioUserId(USER_ID)).hasSize(1);
    }

    @Test
    void getAchievements_returnsEmptyList_whenNoAchievements() throws Exception {
        createPortfolioInDb(USER_ID);

        mockMvc.perform(TestSecurityHelper.withStudent(
                        get("/api/v1/portfolios/{userId}/achievements", USER_ID), USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void deleteAchievement_happyPath_removesFromDb() throws Exception {
        Portfolio portfolio = createPortfolioInDb(USER_ID);
        Achievement achievement = achievementRepository.save(Achievement.builder()
                .portfolio(portfolio)
                .type(Achievement.AchievementType.PROJECT)
                .title("Test Achievement")
                .build());

        mockMvc.perform(TestSecurityHelper.withStudent(
                        delete("/api/v1/portfolios/achievements/{achievementId}", achievement.getId()), USER_ID))
                .andExpect(status().isNoContent());

        assertThat(achievementRepository.findByPortfolioUserId(USER_ID)).isEmpty();
    }

    // --- Skills ---

    @Test
    void addSkill_happyPath_createsAndReturns201() throws Exception {
        createPortfolioInDb(USER_ID);

        CreateSkillDto dto = new CreateSkillDto("Java", 4);

        mockMvc.perform(TestSecurityHelper.withStudent(
                        post("/api/v1/portfolios/{userId}/skills", USER_ID), USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Java"))
                .andExpect(jsonPath("$.level").value(4));

        assertThat(skillRepository.findByPortfolioUserId(USER_ID)).hasSize(1);
    }

    @Test
    void deleteSkill_happyPath_removesFromDb() throws Exception {
        Portfolio portfolio = createPortfolioInDb(USER_ID);
        PortfolioSkill skill = skillRepository.save(PortfolioSkill.builder()
                .portfolio(portfolio)
                .name("Python")
                .level(3)
                .build());

        mockMvc.perform(TestSecurityHelper.withStudent(
                        delete("/api/v1/portfolios/skills/{skillId}", skill.getId()), USER_ID))
                .andExpect(status().isNoContent());

        assertThat(skillRepository.findByPortfolioUserId(USER_ID)).isEmpty();
    }

    @Test
    void searchBySkill_returnsMatchingSkills() throws Exception {
        Portfolio portfolio = createPortfolioInDb(USER_ID);
        Portfolio secondPortfolio = createPortfolioInDb(UUID.randomUUID());
        skillRepository.save(PortfolioSkill.builder()
                .portfolio(portfolio)
                .name("Java")
                .level(4)
                .build());
        skillRepository.save(PortfolioSkill.builder()
                .portfolio(secondPortfolio)
                .name("Java")
                .level(2)
                .build());

        mockMvc.perform(TestSecurityHelper.withStudent(
                        get("/api/v1/portfolios/search")
                                .param("skill", "Java")
                                .param("minLevel", "3"), USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].level").value(4));
    }

    @Test
    void verifySkill_lecturerOnly_studentGetsForbidden() throws Exception {
        Portfolio portfolio = createPortfolioInDb(USER_ID);
        PortfolioSkill skill = skillRepository.save(PortfolioSkill.builder()
                .portfolio(portfolio)
                .name("SQL")
                .level(3)
                .build());

        mockMvc.perform(TestSecurityHelper.withStudent(
                        patch("/api/v1/portfolios/skills/{skillId}/verify", skill.getId())
                                .param("verifiedBy", ADMIN_ID.toString()), USER_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    void verifySkill_lecturer_succeeds() throws Exception {
        Portfolio portfolio = createPortfolioInDb(USER_ID);
        PortfolioSkill skill = skillRepository.save(PortfolioSkill.builder()
                .portfolio(portfolio)
                .name("SQL")
                .level(3)
                .build());
        UUID lecturerId = UUID.randomUUID();

        mockMvc.perform(TestSecurityHelper.withLecturer(
                        patch("/api/v1/portfolios/skills/{skillId}/verify", skill.getId())
                                .param("verifiedBy", lecturerId.toString()), lecturerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"));

        PortfolioSkill updated = skillRepository.findById(skill.getId()).orElseThrow();
        assertThat(updated.getVerificationStatus()).isEqualTo(PortfolioSkill.VerificationStatus.VERIFIED);
    }

    // --- Helpers ---

    private Portfolio createPortfolioInDb(UUID userId) {
        return portfolioRepository.save(Portfolio.builder()
                .userId(userId)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build());
    }
}
