package net.theevilreaper.otis.exception;

public class ObjectConversationException extends RuntimeException {

    public ObjectConversationException(String message) {
        super(message);
    }

    public ObjectConversationException(String message, Throwable cause) {
        super(message, cause);
    }
}
