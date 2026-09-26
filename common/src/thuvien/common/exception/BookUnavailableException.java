package thuvien.common.exception;

public class BookUnavailableException extends LibraryException {
    private static final long serialVersionUID = 1L;

    public BookUnavailableException(String message) {
        super(message);
    }
}
