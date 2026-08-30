package com.metrix.api.platform;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import org.springframework.dao.DataAccessException;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;

/**
 * Enruta cada operación MongoDB a la BD del tenant activo o a la BD por defecto.
 */
public class TenantAwareMongoDatabaseFactory extends SimpleMongoClientDatabaseFactory {

    public TenantAwareMongoDatabaseFactory(MongoClient mongoClient, String defaultDatabaseName) {
        super(mongoClient, defaultDatabaseName);
    }

    @Override
    public MongoDatabase getMongoDatabase() throws DataAccessException {
        String tenantDb = TenantContext.getDatabaseName();
        if (tenantDb != null && !tenantDb.isBlank()) {
            return getMongoClient().getDatabase(tenantDb);
        }
        return super.getMongoDatabase();
    }

    @Override
    public MongoDatabase getMongoDatabase(String dbName) throws DataAccessException {
        String tenantDb = TenantContext.getDatabaseName();
        if (tenantDb != null && !tenantDb.isBlank()) {
            return getMongoClient().getDatabase(tenantDb);
        }
        return super.getMongoDatabase(dbName);
    }
}
