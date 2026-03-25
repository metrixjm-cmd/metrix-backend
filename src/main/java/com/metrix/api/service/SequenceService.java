package com.metrix.api.service;

import com.metrix.api.model.Sequence;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Servicio de secuencias atómicas para generación de folios.
 * <p>
 * Usa {@code findAndModify} de MongoDB que es atómico a nivel de documento,
 * lo que garantiza unicidad incluso bajo alta concurrencia.
 * <p>
 * Mapeo de prefijos por puesto:
 * <pre>
 *   Administrador → ADM
 *   Gerente       → GER
 *   Cajero        → CAJ
 *   Cocinero      → COC
 *   Mesero        → MES
 *   Supervisor    → SUP
 *   (default)     → COL (Colaborador)
 * </pre>
 */
@Service
@RequiredArgsConstructor
public class SequenceService {

    private final MongoOperations mongoOperations;

    private static final Map<String, String> PUESTO_PREFIXES = Map.ofEntries(
            Map.entry("ADMINISTRADOR", "ADM"),
            Map.entry("ADMIN", "ADM"),
            Map.entry("ADMIN GENERAL", "ADM"),
            Map.entry("GERENTE", "GER"),
            Map.entry("GERENTE DE SUCURSAL", "GER"),
            Map.entry("GERENTE DE TURNO", "GER"),
            Map.entry("CAJERO", "CAJ"),
            Map.entry("COCINERO", "COC"),
            Map.entry("MESERO", "MES"),
            Map.entry("SUPERVISOR", "SUP"),
            Map.entry("SUPERVISOR DE PISO", "SUP"),
            Map.entry("OPERADOR", "OPE"),
            Map.entry("OPERADOR DE PISO", "OPE"),
            Map.entry("REPARTIDOR", "REP"),
            Map.entry("BARISTA", "BAR"),
            Map.entry("HOSTESS", "HOS"),
            Map.entry("ALMACENISTA", "ALM"),
            Map.entry("LIMPIEZA", "LIM"),
            Map.entry("CAPACITADOR", "CAP"),
            Map.entry("TRAINER", "TRN")
    );

    /**
     * Obtiene el siguiente valor de la secuencia (atómico, thread-safe).
     *
     * @param sequenceName nombre de la secuencia (ej. "user_GER", "store")
     * @return siguiente valor consecutivo (empezando en 1)
     */
    public long getNextValue(String sequenceName) {
        Sequence seq = mongoOperations.findAndModify(
                Query.query(Criteria.where("_id").is(sequenceName)),
                new Update().inc("value", 1),
                FindAndModifyOptions.options().returnNew(true).upsert(true),
                Sequence.class
        );
        return seq != null ? seq.getValue() : 1;
    }

    /**
     * Consulta el próximo valor SIN incrementar (solo lectura).
     * Útil para mostrar un preview en el formulario antes de guardar.
     */
    public long peekNextValue(String sequenceName) {
        Sequence seq = mongoOperations.findOne(
                Query.query(Criteria.where("_id").is(sequenceName)),
                Sequence.class
        );
        return seq != null ? seq.getValue() + 1 : 1;
    }

    /** Preview del próximo folio de sucursal sin consumirlo. */
    public String peekNextStoreCode() {
        return String.format("SUC%03d", peekNextValue("store"));
    }

    /**
     * Resuelve el prefijo combinando rol + puesto.
     * <ul>
     *   <li>ADMIN   → ADM  (siempre, sin importar puesto)</li>
     *   <li>GERENTE → GER  (siempre, sin importar puesto)</li>
     *   <li>EJECUTADOR → prefijo del puesto (CAJ, MES, COC...)</li>
     * </ul>
     */
    public String resolvePrefixByRolAndPuesto(String rol, String puesto) {
        if (rol != null) {
            String rolUpper = rol.trim().toUpperCase();
            if (rolUpper.equals("ADMIN"))   return "ADM";
            if (rolUpper.equals("GERENTE")) return "GER";
        }
        return resolvePrefix(puesto);
    }

    /** Preview del próximo folio (rol + puesto), sin consumirlo. */
    public String peekNextUserFolio(String rol, String puesto) {
        String prefix = resolvePrefixByRolAndPuesto(rol, puesto);
        return String.format("%s%03d", prefix, peekNextValue("user_" + prefix));
    }

    /** Sobrecarga por compatibilidad (solo puesto). */
    public String peekNextUserFolio(String puesto) {
        return peekNextUserFolio(null, puesto);
    }

    /**
     * Genera un folio de usuario atómico: [PREFIX][FOLIO_3_DIGITOS]
     * El prefijo se determina por rol primero, luego por puesto.
     */
    public String generateUserFolio(String rol, String puesto) {
        String prefix = resolvePrefixByRolAndPuesto(rol, puesto);
        long folio = getNextValue("user_" + prefix);
        return String.format("%s%03d", prefix, folio);
    }

    /** Sobrecarga por compatibilidad (solo puesto). */
    public String generateUserFolio(String puesto) {
        return generateUserFolio(null, puesto);
    }

    /**
     * Genera un código de sucursal: SUC[FOLIO_3_DIGITOS]
     * <p>
     * Ejemplo: "SUC001", "SUC002"...
     *
     * @return código único generado
     */
    public String generateStoreCode() {
        long folio = getNextValue("store");
        return String.format("SUC%03d", folio);
    }

    /**
     * Resuelve el prefijo de 3 letras a partir del nombre del puesto.
     * Si no se encuentra en el mapeo, toma las primeras 3 letras del puesto.
     */
    public String resolvePrefix(String puesto) {
        if (puesto == null || puesto.isBlank()) return "COL";
        String upper = puesto.trim().toUpperCase();
        String prefix = PUESTO_PREFIXES.get(upper);
        if (prefix != null) return prefix;

        // Fallback: primeras 3 letras del puesto
        String clean = upper.replaceAll("[^A-Z]", "");
        return clean.length() >= 3 ? clean.substring(0, 3) : "COL";
    }
}
