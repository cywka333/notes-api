package com.example.notes.item.permission;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
class SharingTargetNotFoundException extends RuntimeException {

    SharingTargetNotFoundException(String message) {
        super(message);
    }
}
