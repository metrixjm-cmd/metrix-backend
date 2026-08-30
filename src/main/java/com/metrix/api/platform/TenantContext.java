package com.metrix.api.platform;

/**
 * Contexto de base de datos por request (tenant operativo o plataforma).
 * <p>
 * Cada restaurante cliente tiene su propia BD MongoDB. El JWT incluye
 * {@code databaseName} y este holder enruta las operaciones de la app.
 */
public final class TenantContext {

    private static final ThreadLocal<String> DATABASE = new ThreadLocal<>();
    private static final ThreadLocal<String> INSTANCE_ID = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> PLATFORM_ADMIN = ThreadLocal.withInitial(() -> false);

    private TenantContext() {
    }

    public static void setDatabaseName(String databaseName) {
        DATABASE.set(databaseName);
    }

    public static String getDatabaseName() {
        return DATABASE.get();
    }

    public static void setInstanceId(String instanceId) {
        INSTANCE_ID.set(instanceId);
    }

    public static String getInstanceId() {
        return INSTANCE_ID.get();
    }

    public static void setPlatformAdmin(boolean platformAdmin) {
        PLATFORM_ADMIN.set(platformAdmin);
    }

    public static boolean isPlatformAdmin() {
        return Boolean.TRUE.equals(PLATFORM_ADMIN.get());
    }

    public static void clear() {
        DATABASE.remove();
        INSTANCE_ID.remove();
        PLATFORM_ADMIN.remove();
    }
}
