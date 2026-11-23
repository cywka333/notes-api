package com.example.notes.item;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ItemController.class)
class ItemExceptionHandler {

    @ExceptionHandler(ItemVersionConflictException.class)
    ProblemDetail versionConflict(ItemVersionConflictException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setProperty("currentVersion", exception.currentVersion());
        return problem;
    }
}
