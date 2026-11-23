package com.example.notes.item.permission;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
class SelfSharingException extends RuntimeException {

    SelfSharingException() {
        super("The owner already has access to this item.");
    }
}
