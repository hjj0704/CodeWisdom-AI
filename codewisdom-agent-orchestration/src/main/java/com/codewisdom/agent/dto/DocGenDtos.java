package com.codewisdom.agent.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public final class DocGenDtos {

    private DocGenDtos() {
    }

    public record TypeDocGenRequest(
            @NotBlank String qualifiedName,
            @NotBlank String kind,
            String classCommentHint,
            @NotNull @Valid List<MethodDocGenRequest> methods
    ) {
    }

    public record MethodDocGenRequest(
            @NotBlank String name,
            String returnType,
            boolean constructor,
            @NotNull @Valid List<ParameterDocGenRequest> parameters
    ) {
    }

    public record ParameterDocGenRequest(
            @NotBlank String name,
            @NotBlank String type
    ) {
    }

    public record DocGenResponseView(
            boolean skipped,
            String typeComment,
            Map<String, String> methodComments,
            String message
    ) {
    }
}
