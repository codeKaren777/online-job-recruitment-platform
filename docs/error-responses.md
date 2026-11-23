# API error responses

All errors use the shared JSON contract below. Successful response bodies and status codes are unchanged.

```json
{
  "timestamp": "2026-11-23T14:35:20Z",
  "status": 404,
  "code": "RESOURCE_NOT_FOUND",
  "message": "The requested resource was not found.",
  "path": "/api/jobs/123",
  "correlationId": "7f8c2a91-6a62-4c68-9bd5-279b68ac4e37"
}
```

Validation errors use `VALIDATION_ERROR`, the message `One or more fields are invalid.`, and may include `fieldErrors` entries with a field name and safe validation message. Error messages never include submitted field values. The server generates an `X-Correlation-ID` for each request, returns it in that response header and error body, and places it in the logging MDC.

| Status | Codes | Meaning |
| --- | --- | --- |
| 400 | `BAD_REQUEST`, `VALIDATION_ERROR`, existing domain validation codes | Malformed or invalid request |
| 401 | `AUTHENTICATION_REQUIRED`, `INVALID_CREDENTIALS` | Missing/invalid authentication |
| 403 | `FORBIDDEN` | Authenticated caller lacks permission |
| 404 | `RESOURCE_NOT_FOUND`, existing `NOT_FOUND` domain codes | Resource does not exist |
| 409 | `CONFLICT`, existing domain conflict codes | Request conflicts with current state |
| 429 | `RATE_LIMIT_EXCEEDED` | Rate-limit integration rejected the request |
| 500 | `INTERNAL_SERVER_ERROR` | Unexpected failure; response is generic and diagnostics are logged server-side |

OpenAPI publishes the reusable `ApiErrorResponse` and `ApiFieldError` schemas and references them from documented error responses. The repository currently has no rate-limiting implementation; an integration can throw `RateLimitExceededException` to use the shared 429 mapping.
