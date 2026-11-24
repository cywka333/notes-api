package com.example.notes.item.web;

import com.example.notes.item.permission.GrantedPermission;
import com.example.notes.item.permission.ItemSharingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/items/{id}/share")
class ItemSharingController {

    private final ItemSharingService sharing;

    ItemSharingController(ItemSharingService sharing) {
        this.sharing = sharing;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<GrantedPermission> share(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt,
                                            @Valid @RequestBody ShareItemRequest request) {
        var result = sharing.share(id, UUID.fromString(jwt.getSubject()), request.userId(), request.role());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.permission());
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(@PathVariable UUID id, @PathVariable UUID userId, @AuthenticationPrincipal Jwt jwt) {
        sharing.revoke(id, UUID.fromString(jwt.getSubject()), userId);
    }
}
