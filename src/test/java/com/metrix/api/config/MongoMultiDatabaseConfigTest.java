package com.metrix.api.config;

import com.metrix.api.platform.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.mongodb.core.MongoTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class MongoMultiDatabaseConfigTest {

    private static final String URI = "mongodb://localhost:27017/metrix_db";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MongoAutoConfiguration.class, MongoDataAutoConfiguration.class))
            .withUserConfiguration(MongoMultiDatabaseConfig.class)
            .withPropertyValues("spring.data.mongodb.uri=" + URI);

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void platformTemplateStaysOnFixedDatabaseWhileTenantTemplateFollowsContext() {
        runner.run(context -> {
            MongoTemplate platform = context.getBean("platformMongoTemplate", MongoTemplate.class);
            MongoTemplate tenant = context.getBean("mongoTemplate", MongoTemplate.class);

            TenantContext.setDatabaseName("metrix_tenant_karina_1135a220");

            assertThat(tenant.getDb().getName()).isEqualTo("metrix_tenant_karina_1135a220");
            assertThat(platform.getDb().getName()).isEqualTo("metrix_db");
        });
    }

    @Test
    void platformDatabaseCanBeSeparatedExplicitly() {
        runner.withPropertyValues("metrix.platform.database-name=metrix_platform").run(context -> {
            MongoTemplate platform = context.getBean("platformMongoTemplate", MongoTemplate.class);

            TenantContext.setDatabaseName("metrix_tenant_karina_1135a220");

            assertThat(platform.getDb().getName()).isEqualTo("metrix_platform");
        });
    }
}
