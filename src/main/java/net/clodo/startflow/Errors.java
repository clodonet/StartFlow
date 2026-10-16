package net.clodo.startflow;

import java.util.concurrent.CompletionException;

final class Errors {
    private Errors() {
    }

    static String concise(Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }
}
