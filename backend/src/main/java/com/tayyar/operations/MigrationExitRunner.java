package com.tayyar.operations;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Closes the application after Flyway and Hibernate validation complete in a migration job. */
@Component
@Profile("container-migrate")
public final class MigrationExitRunner implements ApplicationRunner {
    private final ConfigurableApplicationContext context;

    public MigrationExitRunner(ConfigurableApplicationContext context) {
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        SpringApplication.exit(context);
    }
}
