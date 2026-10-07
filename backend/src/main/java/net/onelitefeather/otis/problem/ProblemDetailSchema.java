package net.onelitefeather.otis.problem;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * OpenAPI description of the RFC 9457 problem document every error response carries. Only used to
 * document the API; the wire format is written by {@link ProblemResponse}.
 */
@Schema(
        name = "ProblemDetail",
        description = "RFC 9457 problem details. Further extension members may be present.",
        additionalProperties = Schema.AdditionalPropertiesValue.TRUE
)
public record ProblemDetailSchema(
        @Schema(description = "URI identifying the kind of problem, 'about:blank' when there is no more specific type.",
                example = "https://otis.onelitefeather.net/problems/constraint-violation",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String type,
        @Schema(description = "Short, human-readable summary of the problem type.",
                example = "Constraint Violation", requiredMode = Schema.RequiredMode.REQUIRED)
        String title,
        @Schema(description = "HTTP status code of the response.", example = "400",
                requiredMode = Schema.RequiredMode.REQUIRED)
        int status,
        @Schema(description = "Explanation specific to this occurrence of the problem.")
        String detail,
        @Schema(description = "URI identifying this occurrence of the problem.")
        String instance,
        @Schema(description = "W3C trace id of the request, present while tracing is enabled.",
                example = "4bf92f3577b34da6a3ce929d0e0e4736")
        String traceId,
        @Schema(description = "Present for constraint violations: one entry per violated constraint.")
        List<Violation> violations
) {

    @Schema(name = "ProblemViolation", description = "A violated request constraint.")
    public record Violation(
            @Schema(description = "Path of the invalid input.", example = "add.playerDTO.playerName")
            String field,
            @Schema(description = "What is wrong with the input.", example = "Username must be between 3 and 16 characters.")
            String message
    ) {
    }
}
