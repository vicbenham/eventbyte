package com.easy.eventbyte.exposition;

import com.easy.eventbyte.domain.InvalidBookingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Traduit les erreurs du domaine (qui ne connaît pas HTTP) en réponses de l'API. */
@RestControllerAdvice
public class DomainExceptionHandler {

    @ExceptionHandler(InvalidBookingException.class)
    public ProblemDetail invalidBooking(InvalidBookingException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
