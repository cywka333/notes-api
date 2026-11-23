package com.example.notes.item;

class ItemVersionConflictException extends RuntimeException {

    private final int currentVersion;

    ItemVersionConflictException(int currentVersion, Throwable cause) {
        super("The item version has changed.", cause);
        this.currentVersion = currentVersion;
    }

    int currentVersion() {
        return currentVersion;
    }
}
