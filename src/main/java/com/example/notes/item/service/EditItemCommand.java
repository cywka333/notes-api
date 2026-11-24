package com.example.notes.item.service;

public record EditItemCommand(int expectedVersion, String title, boolean contentPresent, String content) {
}
