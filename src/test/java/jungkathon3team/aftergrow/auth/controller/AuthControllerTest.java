package jungkathon3team.aftergrow.auth.controller;

import jungkathon3team.aftergrow.auth.entity.User;
import jungkathon3team.aftergrow.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final String SIGNUP_BODY = """
            { "email": "runner@example.com", "password": "password123", "nickname": "김러너",
              "agreeTerms": true, "agreePrivacy": true, "agreeMarketing": false }
            """;

    @BeforeEach
    void clean() {
        userRepository.deleteAll();
    }

    @Test
    void 회원가입에_성공하면_201과_래핑된_응답을_반환한다() throws Exception {
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SIGNUP_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.data.userId").exists())
                .andExpect(jsonPath("$.data.email").value("runner@example.com"))
                .andExpect(jsonPath("$.data.nickname").value("김러너"))
                .andExpect(jsonPath("$.data.createdAt").exists());
    }

    @Test
    void 비밀번호는_평문이_아니라_해시로_저장된다() throws Exception {
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SIGNUP_BODY))
                .andExpect(status().isCreated());

        User saved = userRepository.findByEmail("runner@example.com").orElseThrow();
        assertThat(saved.getPasswordHash()).isNotEqualTo("password123");
        assertThat(passwordEncoder.matches("password123", saved.getPasswordHash())).isTrue();
    }

    @Test
    void 응답_본문에_비밀번호가_포함되지_않는다() throws Exception {
        String body = mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SIGNUP_BODY))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("password123");
        assertThat(body).doesNotContain("passwordHash");
    }

    /**
     * User.createdAt이 LocalDateTime이라 타임존 오프셋이 붙지 않습니다.
     * 엔티티를 OffsetDateTime으로 바꾸면 이 테스트가 깨지므로,
     * 그때 API 명세서의 날짜 표기도 함께 고쳐야 한다는 알림 역할을 합니다.
     */
    @Test
    void createdAt은_오프셋_없는_ISO_로컬_시각으로_내려간다() throws Exception {
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SIGNUP_BODY))
                .andExpect(jsonPath("$.data.createdAt")
                        .value(org.hamcrest.Matchers.matchesPattern("\\d{4}-\\d{2}-\\d{2}T[\\d:.]+")));
    }

    @Test
    void 이미_가입된_이메일이면_409와_E4091을_반환한다() throws Exception {
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SIGNUP_BODY))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SIGNUP_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error.code").value("E4091"));
    }

    @Test
    void 이메일_형식이_아니면_400과_E4001을_반환한다() throws Exception {
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "not-an-email", "password": "password123", "nickname": "김러너",
                                  "agreeTerms": true, "agreePrivacy": true }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("E4001"))
                .andExpect(jsonPath("$.error.message").value("이메일 형식이 올바르지 않습니다."));
    }

    /** 클라이언트가 깨진 본문을 보낸 것이므로 500이 아니라 400이어야 합니다. */
    @Test
    void 깨진_JSON_본문은_400과_E4001을_반환한다() throws Exception {
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\",\"password\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("E4001"));
    }

    @Test
    void UTF_8이_아닌_바이트가_섞인_본문은_400과_E4001을_반환한다() throws Exception {
        byte[] invalidUtf8 = new byte[]{
                '{', '"', 'n', 'i', 'c', 'k', 'n', 'a', 'm', 'e', '"', ':', '"',
                (byte) 0xB1, (byte) 0xE8, '"', '}'};

        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidUtf8))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("E4001"));
    }

    @Test
    void 비밀번호가_8자_미만이면_400과_E4001을_반환한다() throws Exception {
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "runner@example.com", "password": "short", "nickname": "김러너",
                                  "agreeTerms": true, "agreePrivacy": true }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("E4001"));
    }

    @Test
    void 필수_약관에_동의하지_않으면_400과_E4001을_반환한다() throws Exception {
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "runner@example.com", "password": "password123", "nickname": "김러너",
                                  "agreeTerms": false, "agreePrivacy": true }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("E4001"))
                .andExpect(jsonPath("$.error.message").value("이용약관 동의는 필수입니다."));

        assertThat(userRepository.findByEmail("runner@example.com")).isEmpty();
    }

    /** @AssertTrue는 null을 유효로 보므로, 필드를 생략하는 것만으로 뚫리지 않는지 확인한다. */
    @Test
    void 약관_동의_필드를_생략해도_400과_E4001을_반환한다() throws Exception {
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "runner@example.com", "password": "password123", "nickname": "김러너" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("E4001"));

        assertThat(userRepository.findByEmail("runner@example.com")).isEmpty();
    }

    @Test
    void 필수_약관_동의_시각이_저장된다() throws Exception {
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SIGNUP_BODY))
                .andExpect(status().isCreated());

        User saved = userRepository.findByEmail("runner@example.com").orElseThrow();
        assertThat(saved.getTermsAgreedAt()).isNotNull();
        assertThat(saved.getPrivacyAgreedAt()).isNotNull();
    }

    @Test
    void 마케팅_동의는_동의한_경우에만_시각이_남는다() throws Exception {
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SIGNUP_BODY))
                .andExpect(status().isCreated());
        assertThat(userRepository.findByEmail("runner@example.com").orElseThrow()
                .getMarketingAgreedAt()).isNull();

        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "marketer@example.com", "password": "password123", "nickname": "김마케",
                                  "agreeTerms": true, "agreePrivacy": true, "agreeMarketing": true }
                                """))
                .andExpect(status().isCreated());
        assertThat(userRepository.findByEmail("marketer@example.com").orElseThrow()
                .getMarketingAgreedAt()).isNotNull();
    }

    @Test
    void 회원가입은_인증_없이_호출할_수_있다() throws Exception {
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SIGNUP_BODY))
                .andExpect(status().isCreated());
    }
}
