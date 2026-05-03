package com.github.leonid353.common.error;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Data
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ErrorResponse {
    Instant timestamp;
    int status;
    String error;
    String message;

    public ErrorResponse(String message) {
        this.timestamp = Instant.now();
        this.status = 500;
        this.error = "Internal Server Error";
        this.message = message;
    }
}
