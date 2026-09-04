package dev.sellerkit.reconkit.domain.money;

public class CurrencyMismatchException extends RuntimeException {

    public CurrencyMismatchException(String left, String right) {
        super("cannot combine " + left + " with " + right);
    }
}
