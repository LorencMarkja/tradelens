package io.github.lorencmarkja.tradelens.common;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String code, String message, String field) {
}
