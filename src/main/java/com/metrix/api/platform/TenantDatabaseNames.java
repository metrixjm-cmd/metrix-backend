package com.metrix.api.platform;

import com.mongodb.ConnectionString;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resuelve nombres de BD para enrutamiento multi-tenant.
 */
@Component
public class TenantDatabaseNames {

    private final String defaultOperationalDatabase;
    private final String platformDatabase;

    public TenantDatabaseNames(
            @Value("${spring.data.mongodb.uri}") String mongoUri,
            @Value("${metrix.platform.database-name:metrix_platform}") String platformDatabase) {
        this.defaultOperationalDatabase = new ConnectionString(mongoUri).getDatabase();
        this.platformDatabase = platformDatabase;
    }

    /** BD operativa legacy / demo (incidencias, tareas, usuarios del tenant principal). */
    public String getDefaultOperationalDatabase() {
        return defaultOperationalDatabase;
    }

    public String getPlatformDatabase() {
        return platformDatabase;
    }

    /**
     * Admin 0 autentica en {@code metrix_platform} pero opera la app en la BD operativa por defecto.
     */
    public String resolveOperationalDatabase(String jwtDatabaseName, boolean platformAdmin) {
        if (!platformAdmin) {
            return jwtDatabaseName;
        }
        if (jwtDatabaseName == null || jwtDatabaseName.isBlank()
                || platformDatabase.equals(jwtDatabaseName)) {
            return defaultOperationalDatabase;
        }
        return jwtDatabaseName;
    }
}
