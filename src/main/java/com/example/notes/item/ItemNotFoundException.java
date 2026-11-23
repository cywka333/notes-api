package com.example.notes.item;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
class ItemNotFoundException extends RuntimeException {

    ItemNotFoundException() {
        super("Item not found.");
    }
}
