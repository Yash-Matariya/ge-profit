package com.geprofit.web;

import com.geprofit.wiki.WikiPricesClient;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Turns exceptions into clean JSON.
 *
 * <p>Without this, a bad query parameter produces a stack trace in the response
 * body -- ugly for the dashboard and a small information leak. Each handler
 * maps a cause to the status code that actually describes it.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** A query parameter that could not be parsed, e.g. sort=NONSENSE. */
    @ExceptionHandler({MethodArgumentTypeMismatchException.class,
            MethodArgumentNotValidException.class,
            IllegalArgumentException.class})
    public ResponseEntity<ApiError> badRequest(Exception e) {
        return ResponseEntity.badRequest()
                .body(new ApiError(HttpStatus.BAD_REQUEST.value(), e.getMessage(), Instant.now()));
    }

    /**
     * The Wiki API is unreachable or returned an error.
     *
     * <p>502 rather than 500: the failure is upstream, not in this service, and
     * a client can reasonably retry. In practice users rarely see this, because
     * requests are served from our database rather than by calling the Wiki.
     */
    @ExceptionHandler(WikiPricesClient.WikiApiException.class)
    public ResponseEntity<ApiError> upstream(WikiPricesClient.WikiApiException e) {
        log.warn("Upstream price API failed", e);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiError(HttpStatus.BAD_GATEWAY.value(),
                        "The price service is unavailable.", Instant.now()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception e) {
        // Log the detail, return a generic message: internals are not the
        // caller's business.
        log.error("Unhandled error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "Something went wrong.", Instant.now()));
    }

    public record ApiError(int status, String message, Instant at) {
    }
}
