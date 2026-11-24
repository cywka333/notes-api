package com.example.notes.item.history;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;

@Entity
@Table(name = "item_revisions")
@RevisionEntity(ItemRevisionListener.class)
public class ItemRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @RevisionNumber
    private long id;

    @RevisionTimestamp
    @Column(nullable = false)
    private long timestamp;

    @Column(name = "changed_by", nullable = false, length = 64)
    private String changedBy;

    public long getId() {
        return id;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getChangedBy() {
        return changedBy;
    }

    void setChangedBy(String login) {
        changedBy = login;
    }
}
