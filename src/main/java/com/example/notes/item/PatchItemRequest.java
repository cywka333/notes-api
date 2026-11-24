package com.example.notes.item;

import com.example.notes.web.JsonStringDeserializer;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.validator.constraints.CodePointLength;

class PatchItemRequest {

    @NotNull @PositiveOrZero
    private Integer version;

    @CodePointLength(max = 255)
    private String title;
    private String content;
    private boolean contentPresent;

    @JsonSetter(value = "version", nulls = Nulls.FAIL)
    void setVersion(JsonNode value) {
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new IllegalArgumentException("Version must be an integer.");
        }
        version = value.intValue();
    }

    @JsonSetter(value = "title", nulls = Nulls.FAIL)
    @JsonDeserialize(using = JsonStringDeserializer.class)
    void setTitle(String value) {
        if (value.isBlank()) {
            throw new IllegalArgumentException("Title must contain text.");
        }
        title = value;
    }

    @JsonSetter("content")
    @JsonDeserialize(using = JsonStringDeserializer.class)
    void setContent(String value) {
        contentPresent = true;
        content = value;
    }

    @JsonIgnore
    @AssertTrue(message = "Content exceeds the UTF-8 byte limit for a note.")
    public boolean isContentWithinStorageLimit() {
        return ItemTextLimits.contentFits(content);
    }

    int version() {
        return version;
    }

    void applyTo(Item item) {
        if (title != null) {
            item.changeTitle(title);
        }
        if (contentPresent) {
            item.changeContent(content);
        }
    }
}
