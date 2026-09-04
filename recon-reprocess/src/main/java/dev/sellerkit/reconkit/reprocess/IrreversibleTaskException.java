package dev.sellerkit.reconkit.reprocess;

/**
 * Thrown when a completed step cannot be undone.
 *
 * <p>Deliberately not a retry. If money has already left, retrying the rollback does not
 * bring it back, and an automatic retry loop against an external system is how a single
 * failure becomes a series of them. It escalates to a person instead.
 */
public class IrreversibleTaskException extends RuntimeException {

    public IrreversibleTaskException(String message) {
        super(message);
    }

    public IrreversibleTaskException(String message, Throwable cause) {
        super(message, cause);
    }
}
