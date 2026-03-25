package com.metrix.api.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Documento MongoDB para secuencias de folios auto-incrementales.
 * <p>
 * Cada documento representa un contador atómico:
 * <ul>
 *   <li>{@code _id = "user_GER"} → folio para Gerentes (GER001, GER002...)</li>
 *   <li>{@code _id = "user_EJE"} → folio para Ejecutadores (EJE001, EJE002...)</li>
 *   <li>{@code _id = "store"}    → folio para Sucursales (SUC001, SUC002...)</li>
 * </ul>
 * El incremento usa {@code findAndModify} atómico de MongoDB (thread-safe).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "sequences")
public class Sequence {

    @Id
    private String id;

    private long value;
}
