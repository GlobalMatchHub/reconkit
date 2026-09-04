package dev.sellerkit.reconkit.app.tenancy;

/**
 * The tenant the current thread is working for.
 *
 * <p>Held in a thread local rather than passed as an argument, so that a query written
 * three layers down cannot forget it. The counterpart of that convenience is that the
 * value must be cleared at the end of every request, which the filter does in a finally
 * block: a pooled thread that keeps a stale tenant will happily serve one organisation's
 * data to the next request that lands on it.
 */
public final class TenantContext {

    /** Used by background work that legitimately spans tenants, such as migrations. */
    public static final String SYSTEM = "system";

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(String tenantId) {
        CURRENT.set(tenantId);
    }

    public static String get() {
        return CURRENT.get();
    }

    public static String require() {
        String tenantId = CURRENT.get();
        if (tenantId == null) {
            throw new IllegalStateException("no tenant bound to the current thread");
        }
        return tenantId;
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static <T> T runAs(String tenantId, java.util.function.Supplier<T> body) {
        String previous = CURRENT.get();
        CURRENT.set(tenantId);
        try {
            return body.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
