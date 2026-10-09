package com.evlarus.spendinglimit.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Real PostgreSQL for integration tests. {@link ServiceConnection} wires the datasource to the container,
 * so tests need nothing but a running Docker. The images are the same as in {@code docker-compose.yml}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    public static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:18-alpine");
    public static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:8-alpine");

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(POSTGRES_IMAGE);
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>(REDIS_IMAGE).withExposedPorts(6379);
    }
}
