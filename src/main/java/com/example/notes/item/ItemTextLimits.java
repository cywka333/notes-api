package com.example.notes.item;

import java.nio.charset.StandardCharsets;

final class ItemTextLimits {

    private static final int MYSQL_TEXT_MAX_BYTES = 65_535;

    private ItemTextLimits() {
    }

    static boolean contentFits(String content) {
        return content == null || content.getBytes(StandardCharsets.UTF_8).length <= MYSQL_TEXT_MAX_BYTES;
    }
}
