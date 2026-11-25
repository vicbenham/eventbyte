package com.easy.eventbyte;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));
    }

    @Bean
    @ServiceConnection
    RabbitMQContainer rabbitContainer() {
        return new RabbitMQContainer(DockerImageName.parse("rabbitmq:latest"));
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:latest")).withExposedPorts(6379);
    }

    static final String MEILISEARCH_KEY = "test-master-key-0123456789";

    @Bean
    GenericContainer<?> meilisearchContainer() {
        return new GenericContainer<>(DockerImageName.parse("getmeili/meilisearch:v1.7"))
                .withEnv("MEILI_MASTER_KEY", MEILISEARCH_KEY)
                .withEnv("MEILI_NO_ANALYTICS", "true")
                .withExposedPorts(7700)
                .waitingFor(Wait.forHttp("/health").forStatusCode(200));
    }

    @Bean
    DynamicPropertyRegistrar meilisearchProperties(GenericContainer<?> meilisearchContainer) {
        return registry -> {
            registry.add("meilisearch.host", () -> "http://%s:%d".formatted(
                    meilisearchContainer.getHost(), meilisearchContainer.getMappedPort(7700)));
            registry.add("meilisearch.api-key", () -> MEILISEARCH_KEY);
        };
    }
}
