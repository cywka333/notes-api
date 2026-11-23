package com.example.notes.item;

import org.hibernate.envers.RevisionType;

import java.time.Instant;

record ItemHistoryEntry(long revision, RevisionType revisionType, Instant timestamp,
                        String changedBy, String title, String content) {
}
