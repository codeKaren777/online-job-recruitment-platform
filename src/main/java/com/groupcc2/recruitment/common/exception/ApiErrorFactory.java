package com.groupcc2.recruitment.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ApiErrorFactory {
    public static final String CORRELATION_ID_ATTRIBUTE = ApiErrorFactory.class.getName() + ".correlationId";

    private ApiErrorFactory() {}

    public static ApiErrorResponse create(HttpServletRequest request, int status, String code,
                                          String message, List<ApiErrorResponse.FieldError> fields) {
        Object correlationId = request.getAttribute(CORRELATION_ID_ATTRIBUTE);
        return new ApiErrorResponse(Instant.now(), status, code, message,
                request.getRequestURI(), correlationId == null ? UUID.randomUUID().toString() : correlationId.toString(),
                fields == null || fields.isEmpty() ? null : List.copyOf(fields));
    }
}
