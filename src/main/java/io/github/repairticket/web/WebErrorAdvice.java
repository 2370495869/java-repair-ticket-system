package io.github.repairticket.web;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice(annotations = Controller.class)
public class WebErrorAdvice {
    @ExceptionHandler(AccessDeniedException.class)
    public ModelAndView accessDenied() {
        return new ModelAndView("error/access-denied", HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ModelAndView notFound(ResponseStatusException exception) {
        if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
            return new ModelAndView("error/not-found", HttpStatus.NOT_FOUND);
        }
        return new ModelAndView("error/access-denied", exception.getStatusCode());
    }
}
