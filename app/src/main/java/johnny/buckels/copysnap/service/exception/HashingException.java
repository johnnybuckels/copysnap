package johnny.buckels.copysnap.service.exception;

public class HashingException extends RuntimeException {

    public HashingException(String message) {
        super(message);
    }

    public HashingException(String message, Throwable cause) {
        super(message, cause);
    }

}
