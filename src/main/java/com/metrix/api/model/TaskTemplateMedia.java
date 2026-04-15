package com.metrix.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * Multimedia opcional asociada a una entrada enriquecida de categorias.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskTemplateMedia {

    @Field("type")
    private String type;

    @Field("url")
    private String url;

    @Field("title")
    private String title;

    @Field("description")
    private String description;
}
