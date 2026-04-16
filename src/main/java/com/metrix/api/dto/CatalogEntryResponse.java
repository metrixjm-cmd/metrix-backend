package com.metrix.api.dto;

import com.metrix.api.model.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogEntryResponse {
    private String id;
    private String type;
    private String value;
    private String label;
    private Role role;
}
