package dev.sellerkit.reconkit.app.tenancy;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;
import java.util.Map;

/**
 * Hands the thread's tenant to Hibernate.
 *
 * <p>With {@code @TenantId} on the mapped superclass this is all that is required:
 * Hibernate appends the predicate to every select and stamps the value on every insert.
 * The isolation therefore does not depend on any repository method remembering to filter,
 * which is the property that matters, because the query that forgets is always the one
 * written in a hurry at the end of a release.
 */
@Component
public class TenantIdentifierResolver
        implements CurrentTenantIdentifierResolver<String>, HibernatePropertiesCustomizer {

    @Override
    public String resolveCurrentTenantIdentifier() {
        String tenantId = TenantContext.get();
        return tenantId == null ? TenantContext.SYSTEM : tenantId;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
    }
}
