package com.zzwen6.familymeal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * 集成测试基类：真实 MySQL 8 + Redis（Testcontainers），整个测试 JVM 共用一套容器和 Spring 上下文。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IntegrationTestBase.TestConfig.class)
public abstract class IntegrationTestBase {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    public static final LocalDate TODAY = LocalDate.of(2026, 11, 12);

    /**
     * 默认用 Testcontainers 启动 MySQL 8 和 Redis。
     * 如果本机 Docker 不能做端口映射，可以自己起好数据库，然后设置环境变量
     * FM_TEST_MYSQL_URL / FM_TEST_MYSQL_USER / FM_TEST_MYSQL_PASSWORD / FM_TEST_REDIS_HOST / FM_TEST_REDIS_PORT 复用外部服务。
     */
    static final String EXTERNAL_MYSQL = System.getenv("FM_TEST_MYSQL_URL");
    static MySQLContainer<?> mysql;
    static GenericContainer<?> redis;

    static {
        if (EXTERNAL_MYSQL == null || EXTERNAL_MYSQL.isBlank()) {
            mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                    .withDatabaseName("family_meal").withUsername("test").withPassword("test")
                    .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");
            redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
            mysql.start();
            redis.start();
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        if (mysql != null) {
            r.add("spring.datasource.url", mysql::getJdbcUrl);
            r.add("spring.datasource.username", mysql::getUsername);
            r.add("spring.datasource.password", mysql::getPassword);
            r.add("spring.data.redis.host", redis::getHost);
            r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        } else {
            r.add("spring.datasource.url", () -> EXTERNAL_MYSQL);
            r.add("spring.datasource.username", () -> env("FM_TEST_MYSQL_USER", "test"));
            r.add("spring.datasource.password", () -> env("FM_TEST_MYSQL_PASSWORD", "test"));
            r.add("spring.data.redis.host", () -> env("FM_TEST_REDIS_HOST", "localhost"));
            r.add("spring.data.redis.port", () -> env("FM_TEST_REDIS_PORT", "6379"));
        }
        // 每次测试运行都从干净的库开始
        r.add("spring.flyway.clean-disabled", () -> "false");
        r.add("app.media.path", () -> MEDIA_DIR);
        r.add("app.media.image-max-bytes", () -> "8192");
        r.add("app.media.video-max-bytes", () -> "16384");
    }

    static final String MEDIA_DIR;
    static {
        try {
            MEDIA_DIR = Files.createTempDirectory("fm-media-test").toAbsolutePath().toString();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static String env(String k, String def) {
        String v = System.getenv(k);
        return v == null || v.isBlank() ? def : v;
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(TODAY.atTime(9, 0), ZONE);
        }

        @Bean
        FlywayMigrationStrategy cleanMigrate() {
            return flyway -> {
                flyway.clean();
                flyway.migrate();
            };
        }
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper om;
    @Autowired protected MutableClock clock;
    @Autowired protected StringRedisTemplate redisTemplate;

    @BeforeEach
    void resetState() {
        clock.set(TODAY.atTime(9, 0));
        var keys = redisTemplate.keys("fm:*");
        if (keys != null && !keys.isEmpty()) redisTemplate.delete(keys);
    }

    protected void setTime(LocalDateTime t) {
        clock.set(t);
    }

    protected record Res(int status, JsonNode body) {}

    protected Res call(HttpMethod method, String url, String token, Object body) throws Exception {
        MockHttpServletRequestBuilder b = request(method, url).accept(MediaType.APPLICATION_JSON);
        if (token != null) b.header("Authorization", "Bearer " + token);
        if (body != null) b.contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body));
        MvcResult r = mvc.perform(b).andReturn();
        String s = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return new Res(r.getResponse().getStatus(), s.isBlank() ? om.nullNode() : om.readTree(s));
    }

    protected Res get(String url, String token) throws Exception { return call(HttpMethod.GET, url, token, null); }
    protected Res post(String url, String token, Object body) throws Exception { return call(HttpMethod.POST, url, token, body); }
    protected Res put(String url, String token, Object body) throws Exception { return call(HttpMethod.PUT, url, token, body); }
    protected Res patch(String url, String token, Object body) throws Exception { return call(HttpMethod.PATCH, url, token, body); }
    protected Res delete(String url, String token) throws Exception { return call(HttpMethod.DELETE, url, token, null); }

    protected String adminToken() throws Exception {
        return post("/api/auth/login", null, java.util.Map.of("username", "admin", "password", "admin-pass-123"))
                .body().get("token").asText();
    }
}
