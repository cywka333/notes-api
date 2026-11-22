package com.example.notes.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;

final class LoginRateLimitFilter extends OncePerRequestFilter {

    static final RequestMatcher LOGIN = PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/login");

    private final LoginRateLimiter limiter;
    private final ObjectMapper json;

    LoginRateLimitFilter(LoginRateLimiter limiter, ObjectMapper json) {
        this.limiter = limiter;
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !LOGIN.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        long retryAfter = limiter.retryAfterSeconds(request.getRemoteAddr());
        if (retryAfter == 0) {
            chain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                "Too many login attempts. Try again later.");
        problem.setInstance(URI.create("/login"));
        json.writeValue(response.getOutputStream(), problem);
    }
}
