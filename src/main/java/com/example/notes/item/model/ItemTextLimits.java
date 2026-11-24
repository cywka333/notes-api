package com.example.notes.item.model;

import java.nio.charset.StandardCharsets;

public final class ItemTextLimits {

    private static final int MYSQL_TEXT_MAX_BYTES = 65_535;

    private ItemTextLimits() {
    }

    public static boolean contentFits(String content) {
        return content == null || content.getBytes(StandardCharsets.UTF_8).length <= MYSQL_TEXT_MAX_BYTES;
    }
}
