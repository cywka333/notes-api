package com.example.notes.item;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/items")
class ItemController {

    private final ItemService items;

    ItemController(ItemService items) {
        this.items = items;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    CreatedItem create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateItemRequest request) {
        return items.create(UUID.fromString(jwt.getSubject()), request.title(), request.content());
    }

    @GetMapping
    List<ListedItem> list(@AuthenticationPrincipal Jwt jwt) {
        return items.list(UUID.fromString(jwt.getSubject()));
    }
}
