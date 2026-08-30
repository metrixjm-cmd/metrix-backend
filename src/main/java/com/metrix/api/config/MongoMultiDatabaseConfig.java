package com.metrix.api.config;

import com.metrix.api.platform.TenantAwareMongoDatabaseFactory;
import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * Configuración dual de MongoDB:
 * <ul>
 *   <li>{@code mongoTemplate} — datos operativos del tenant (ruteado por {@link com.metrix.api.platform.TenantContext})</li>
 *   <li>{@code platformMongoTemplate} — catálogo, órdenes e instancias METRIX</li>
 * </ul>
 */
@Configuration
@EnableMongoRepositories(
        basePackages = "com.metrix.api.repository",
        mongoTemplateRef = "mongoTemplate"
)
public class MongoMultiDatabaseConfig {

    @Value("${spring.data.mongodb.uri}")
    private String mongoUri;

    @Value("${metrix.platform.database-name:metrix_platform}")
    private String platformDatabaseName;

    @Bean
    public MongoClient mongoClient() {
        return MongoClients.create(mongoUri);
    }

    @Bean
    @Primary
    public MongoDatabaseFactory mongoDatabaseFactory(MongoClient mongoClient) {
        String defaultDb = new ConnectionString(mongoUri).getDatabase();
        return new TenantAwareMongoDatabaseFactory(mongoClient, defaultDb);
    }

    @Bean
    @Primary
    public MongoTemplate mongoTemplate(MongoDatabaseFactory mongoDatabaseFactory,
                                       MongoConverter mongoConverter) {
        return new MongoTemplate(mongoDatabaseFactory, mongoConverter);
    }

    @Bean
    public MongoDatabaseFactory platformMongoDatabaseFactory(MongoClient mongoClient) {
        return new TenantAwareMongoDatabaseFactory(mongoClient, platformDatabaseName);
    }

    @Bean(name = "platformMongoTemplate")
    public MongoTemplate platformMongoTemplate(MongoClient mongoClient,
                                               MappingMongoConverter mongoConverter) {
        MongoDatabaseFactory factory = new TenantAwareMongoDatabaseFactory(mongoClient, platformDatabaseName);
        return new MongoTemplate(factory, mongoConverter);
    }
}
