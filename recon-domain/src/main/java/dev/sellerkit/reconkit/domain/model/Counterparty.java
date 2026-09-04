package dev.sellerkit.reconkit.domain.model;

import dev.sellerkit.reconkit.domain.enums.ChannelType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A party we settle with: a gateway, a marketplace, an app store, a bank. */
@Entity
@Table(name = "counterparty")
public class Counterparty extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel_type", nullable = false, length = 24)
    private ChannelType channelType;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "KRW";

    /** Name of the CSV column mapping profile used to load this party's statements. */
    @Column(name = "mapping_profile", nullable = false, length = 60)
    private String mappingProfile;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Embedded
    private SettlementTerms terms = new SettlementTerms();

    protected Counterparty() {
    }

    public Counterparty(String code, String name, ChannelType channelType, String currency, String mappingProfile) {
        this.code = code;
        this.name = name;
        this.channelType = channelType;
        this.currency = currency;
        this.mappingProfile = mappingProfile;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public ChannelType getChannelType() {
        return channelType;
    }

    public String getCurrency() {
        return currency;
    }

    public String getMappingProfile() {
        return mappingProfile;
    }

    public void setMappingProfile(String v) {
        this.mappingProfile = v;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean v) {
        this.active = v;
    }

    public SettlementTerms getTerms() {
        return terms;
    }

    public void setTerms(SettlementTerms v) {
        this.terms = v;
    }
}
