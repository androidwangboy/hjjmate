package vip.mate.agent.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vip.mate.agent.api.service.AgentApiException;

import java.util.LinkedHashMap;
import java.util.Map;

/** Keeps public API errors independent from the internal R<T> envelope. */
@RestControllerAdvice(basePackageClasses = AgentApiPublicController.class)
public class AgentApiExceptionHandler {

    @ExceptionHandler(AgentApiException.class)
    public ResponseEntity<Map<String, Object>> handle(AgentApiException exception) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", exception.getErrorCode());
        error.put("message", exception.getMessage());
        if (!exception.getDetails().isEmpty()) error.put("details", exception.getDetails());
        return ResponseEntity.status(exception.getHttpStatus()).body(Map.of("error", error));
    }
}
