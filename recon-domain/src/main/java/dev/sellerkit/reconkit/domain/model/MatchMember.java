package dev.sellerkit.reconkit.domain.model;

import dev.sellerkit.reconkit.domain.enums.EntrySide;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Membership of one entry in one match group. */
@Entity
@Table(name = "match_member")
public class MatchMember extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false, length = 12)
    private EntrySide side;

    @Column(name = "entry_id", nullable = false)
    private Long entryId;

    protected MatchMember() {
    }

    public MatchMember(Long groupId, EntrySide side, Long entryId) {
        this.groupId = groupId;
        this.side = side;
        this.entryId = entryId;
    }

    public Long getId() {
        return id;
    }

    public Long getGroupId() {
        return groupId;
    }

    public EntrySide getSide() {
        return side;
    }

    public Long getEntryId() {
        return entryId;
    }
}
