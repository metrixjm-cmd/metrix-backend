package com.metrix.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@EnableMongoRepositories(
        basePackages = "com.metrix.api.platform.repository",
        mongoTemplateRef = "platformMongoTemplate"
)
public class PlatformMongoRepositoriesConfig {
}
