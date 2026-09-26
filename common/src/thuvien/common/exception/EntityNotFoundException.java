package thuvien.common.exception;

public class EntityNotFoundException extends LibraryException {
    private static final long serialVersionUID = 1L;

    public EntityNotFoundException(String message) {
        super(message);
    }
}
