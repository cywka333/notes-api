package com.example.notes.item.history;

import org.hibernate.envers.RevisionType;

import java.time.Instant;

public record ItemHistoryEntry(long revision, RevisionType revisionType, Instant timestamp,
                               String changedBy, String title, String content) {
}
