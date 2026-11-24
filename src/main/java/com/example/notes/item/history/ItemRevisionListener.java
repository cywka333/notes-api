package com.example.notes.item.history;

import org.hibernate.envers.RevisionListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public class ItemRevisionListener implements RevisionListener {

    private final JdbcTemplate jdbc;

    public ItemRevisionListener(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void newRevision(Object revisionEntity) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt) || !jwt.isAuthenticated()) {
            throw new IllegalStateException("An authenticated user is required to audit an item change.");
        }

        String login = jdbc.queryForObject("SELECT login FROM users WHERE id = UUID_TO_BIN(?)",
                String.class, jwt.getToken().getSubject());
        ((ItemRevision) revisionEntity).setChangedBy(login);
    }
}
