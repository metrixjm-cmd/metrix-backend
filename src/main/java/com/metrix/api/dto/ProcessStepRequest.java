package com.metrix.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessStepRequest {

    @NotBlank(message = "El título del paso es obligatorio")
    private String title;

    private String description;

    private List<String> tags;
}
