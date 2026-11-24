package com.example.notes.item.web;

import com.example.notes.item.model.ItemTextLimits;
import com.example.notes.web.JsonStringDeserializer;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.CodePointLength;

import java.nio.charset.StandardCharsets;

record CreateItemRequest(
        @JsonDeserialize(using = JsonStringDeserializer.class)
        @NotBlank @CodePointLength(max = 255) String title,
        @JsonDeserialize(using = JsonStringDeserializer.class) String content
) {
    private static final int MYSQL_TEXT_MAX_BYTES = 65_535;

    @JsonIgnore
    @AssertTrue(message = "Content exceeds the UTF-8 byte limit for a note.")
    public boolean isContentWithinStorageLimit() {
        return ItemTextLimits.contentFits(content);
    }
}