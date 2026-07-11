package ru.invest.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.Network;
import org.testcontainers.postgresql.PostgreSQLContainer;
import ru.invest.api.config.InvestApiTestConfiguration;

import java.nio.charset.StandardCharsets;

@SpringBootTest(classes = InvestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@Import(InvestApiTestConfiguration.class)
@ContextConfiguration(initializers = AbstractInvestApplicationTest.Initializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class AbstractInvestApplicationTest {
    @Autowired
    private ObjectMapper objectMapper;

    public static final PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer("postgres:17.6")
            .withDatabaseName("hotels")
            .withUsername("sa")
            .withPassword("sa")
            .withNetwork(Network.SHARED)
            .withReuse(true);

    static {
        postgreSQLContainer.start();
        Runtime.getRuntime().addShutdownHook(new Thread(postgreSQLContainer::stop));

    }

    static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        public void initialize(final ConfigurableApplicationContext configurableApplicationContext) {
            TestPropertyValues
                    .of(
                            "spring.datasource.hikari.jdbc-url=" + postgreSQLContainer.getJdbcUrl(),
                            "spring.datasource.hikari.username=" + postgreSQLContainer.getUsername(),
                            "spring.datasource.hikari.password=" + postgreSQLContainer.getPassword()
                    )
                    .applyTo(configurableApplicationContext.getEnvironment());
        }
    }

    @SneakyThrows
    protected <T> T readObjectFromFile(final String json, final Class<T> clazz) {
        return objectMapper.readValue(IOUtils.resourceToString(json, StandardCharsets.UTF_8), clazz);
    }
}
