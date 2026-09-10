package com.metrix.api.config;

import com.metrix.api.platform.TenantAwareMongoDatabaseFactory;
import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * Configuración dual de MongoDB:
 * <ul>
 *   <li>{@code mongoTemplate} — datos operativos del tenant (ruteado por {@link com.metrix.api.platform.TenantContext})</li>
 *   <li>{@code platformMongoTemplate} — catálogo, órdenes e instancias METRIX (BD fija, sin {@link TenantContext})</li>
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

    @Value("${metrix.platform.database-name:}")
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

    /**
     * BD de plataforma siempre fija: si usara {@link TenantAwareMongoDatabaseFactory},
     * el JWT de Admin 0 (tenant operativo {@code metrix_db}) desviaría las lecturas de
     * {@code platform_users} a la BD equivocada y la autenticación devolvería 403.
     */
    @Bean
    public MongoDatabaseFactory platformMongoDatabaseFactory(MongoClient mongoClient) {
        return new SimpleMongoClientDatabaseFactory(mongoClient, resolvePlatformDatabase());
    }

    /**
     * El {@link Qualifier} no es opcional: {@code mongoDatabaseFactory} es {@code @Primary} y
     * Spring resuelve la primaria antes que el nombre del parámetro, así que sin él este template
     * recibía la fábrica ruteada por {@link com.metrix.api.platform.TenantContext} y los
     * repositorios de plataforma escribían dentro de la BD del tenant activo.
     */
    @Bean(name = "platformMongoTemplate")
    public MongoTemplate platformMongoTemplate(
            @Qualifier("platformMongoDatabaseFactory") MongoDatabaseFactory platformMongoDatabaseFactory,
            MappingMongoConverter mongoConverter) {
        return new MongoTemplate(platformMongoDatabaseFactory, mongoConverter);
    }

    private String resolvePlatformDatabase() {
        return platformDatabaseName == null || platformDatabaseName.isBlank()
                ? new ConnectionString(mongoUri).getDatabase()
                : platformDatabaseName;
    }
}
