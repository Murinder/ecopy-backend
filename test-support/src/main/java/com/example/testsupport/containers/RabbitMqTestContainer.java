package com.example.testsupport.containers;

import org.testcontainers.containers.RabbitMQContainer;

/**
 * Singleton RabbitMQ container shared across all integration tests.
 */
public final class RabbitMqTestContainer {

    private static final RabbitMQContainer CONTAINER =
            new RabbitMQContainer("rabbitmq:3.12-management");

    static {
        CONTAINER.start();
    }

    private RabbitMqTestContainer() {}

    public static RabbitMQContainer getInstance() {
        return CONTAINER;
    }

    public static String getHost() {
        return CONTAINER.getHost();
    }

    public static int getAmqpPort() {
        return CONTAINER.getAmqpPort();
    }

    public static String getUsername() {
        return CONTAINER.getAdminUsername();
    }

    public static String getPassword() {
        return CONTAINER.getAdminPassword();
    }
}
