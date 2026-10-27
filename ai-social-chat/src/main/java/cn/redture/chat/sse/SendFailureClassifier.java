package cn.redture.chat.sse;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.springframework.http.converter.HttpMessageConversionException;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Set;

final class SendFailureClassifier {
    enum Failure {
        PEER_DISCONNECTED,
        EMITTER_ENDED,
        WRITE_TIMEOUT,
        SERIALIZATION,
        TRANSPORT,
        APPLICATION
    }

    static Failure classify(Throwable error) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Failure result = Failure.APPLICATION;
        boolean disconnected = false;
        for (Throwable cause = error;
                cause != null && visited.add(cause);
                cause = cause.getCause()) {
            String name = cause.getClass().getSimpleName();
            String message = String.valueOf(cause.getMessage()).toLowerCase(Locale.ROOT);
            if (cause instanceof SocketTimeoutException) return Failure.WRITE_TIMEOUT;
            if (name.equals("ClientAbortException")
                    || name.equals("AsyncRequestNotUsableException")
                    || message.contains("broken pipe")
                    || message.contains("connection reset")
                    || message.contains("forcibly closed")
                    || cause instanceof java.io.EOFException) {
                disconnected = true;
            }
            if (cause instanceof JsonProcessingException
                    || cause instanceof HttpMessageConversionException) {
                result = Failure.SERIALIZATION;
            } else if (cause instanceof IOException && result != Failure.SERIALIZATION) {
                result = Failure.TRANSPORT;
            } else if (cause instanceof IllegalStateException && result == Failure.APPLICATION) {
                result = Failure.EMITTER_ENDED;
            }
        }
        return disconnected ? Failure.PEER_DISCONNECTED : result;
    }
}
