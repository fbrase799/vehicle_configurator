package com.configurator.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DatabaseInitializer implements ApplicationRunner {

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    public DatabaseInitializer(DataSource dataSource, JdbcTemplate jdbc) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbc.execute("PRAGMA foreign_keys = ON");

        if (isSeeded()) {
            return;
        }

        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource("db/001-init.sql"));
        populator.setSeparator(";");
        populator.setCommentPrefix("--");
        populator.execute(dataSource);
    }

    private boolean isSeeded() {
        if (!tableExists("car_models")) {
            return false;
        }
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM car_models", Integer.class);
        return count != null && count > 0;
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = ?",
            Integer.class,
            tableName);
        return count != null && count > 0;
    }
}
