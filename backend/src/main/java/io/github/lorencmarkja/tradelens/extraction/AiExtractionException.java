package io.github.lorencmarkja.tradelens.extraction;

import org.springframework.http.HttpStatus;

public class AiExtractionException extends RuntimeException {

    public enum Kind {
        TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "AI_TIMEOUT"),
        UNAVAILABLE(HttpStatus.BAD_GATEWAY, "AI_UNAVAILABLE"),
        INVALID_RESPONSE(HttpStatus.BAD_GATEWAY, "AI_INVALID_RESPONSE"),
        DEMO_MESSAGE_NOT_RECOGNISED(HttpStatus.UNPROCESSABLE_CONTENT, "DEMO_MESSAGE_NOT_RECOGNISED");

        public final HttpStatus status;
        public final String code;

        Kind(HttpStatus status, String code) {
            this.status = status;
            this.code = code;
        }
    }

    private final Kind kind;

    public AiExtractionException(Kind kind, String message) {
        this(kind, message, null);
    }

    public AiExtractionException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
