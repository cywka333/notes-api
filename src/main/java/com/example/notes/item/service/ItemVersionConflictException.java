package com.example.notes.item.service;

public class ItemVersionConflictException extends RuntimeException {

    private final int currentVersion;

    ItemVersionConflictException(int currentVersion, Throwable cause) {
        super("The item version has changed.", cause);
        this.currentVersion = currentVersion;
    }

    public int currentVersion() {
        return currentVersion;
    }
}
