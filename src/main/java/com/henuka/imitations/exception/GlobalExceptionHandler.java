package com.henuka.imitations.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    // Handle entity not found exceptions
    @ExceptionHandler(EntityNotFoundException.class)
    public ModelAndView handleEntityNotFoundException(EntityNotFoundException ex, HttpServletRequest request) {
        if (isApiRequest(request)) {
            return new ModelAndView("error")
                .addObject("status", HttpStatus.NOT_FOUND.value())
                .addObject("error", "Not Found")
                .addObject("message", ex.getMessage())
                .addObject("path", request.getRequestURI());
        }
        
        return new ModelAndView("error")
            .addObject("status", HttpStatus.NOT_FOUND.value())
            .addObject("error", "Not Found")
            .addObject("message", ex.getMessage());
    }

    // Handle invalid input exceptions
    @ExceptionHandler(InvalidInputException.class)
    public ModelAndView handleInvalidInputException(InvalidInputException ex, HttpServletRequest request) {
        if (isApiRequest(request)) {
            return new ModelAndView("error")
                .addObject("status", HttpStatus.BAD_REQUEST.value())
                .addObject("error", "Bad Request")
                .addObject("message", ex.getMessage())
                .addObject("path", request.getRequestURI());
        }
        
        return new ModelAndView("error")
            .addObject("status", HttpStatus.BAD_REQUEST.value())
            .addObject("error", "Bad Request")
            .addObject("message", ex.getMessage());
    }

    // Handle validation exceptions
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", new Date());
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("error", "Validation Failed");
        response.put("errors", errors);

        return ResponseEntity.badRequest().body(response);
    }

    // Handle business logic exceptions
    @ExceptionHandler(BusinessException.class)
    public ModelAndView handleBusinessException(BusinessException ex, HttpServletRequest request) {
        if (isApiRequest(request)) {
            return new ModelAndView("error")
                .addObject("status", HttpStatus.UNPROCESSABLE_ENTITY.value())
                .addObject("error", "Business Rule Violation")
                .addObject("message", ex.getMessage())
                .addObject("path", request.getRequestURI());
        }
        
        return new ModelAndView("error")
            .addObject("status", HttpStatus.UNPROCESSABLE_ENTITY.value())
            .addObject("error", "Business Rule Violation")
            .addObject("message", ex.getMessage());
    }

    // Handle all other exceptions
    @ExceptionHandler(Exception.class)
    public ModelAndView handleAllExceptions(Exception ex, HttpServletRequest request) {
        if (isApiRequest(request)) {
            return new ModelAndView("error")
                .addObject("status", HttpStatus.INTERNAL_SERVER_ERROR.value())
                .addObject("error", "Internal Server Error")
                .addObject("message", "An unexpected error occurred")
                .addObject("path", request.getRequestURI());
        }
        
        return new ModelAndView("error")
            .addObject("status", HttpStatus.INTERNAL_SERVER_ERROR.value())
            .addObject("error", "Internal Server Error")
            .addObject("message", "An unexpected error occurred");
    }

    // Helper method to check if the request is an API request
    private boolean isApiRequest(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/");
    }
}

// Custom exceptions
class EntityNotFoundException extends RuntimeException {
    public EntityNotFoundException(String message) {
        super(message);
    }
}

class InvalidInputException extends RuntimeException {
    public InvalidInputException(String message) {
        super(message);
    }
}

class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}

class OrderException extends BusinessException {
    public OrderException(String message) {
        super(message);
    }
}

class ProductException extends BusinessException {
    public ProductException(String message) {
        super(message);
    }
}

class CartException extends BusinessException {
    public CartException(String message) {
        super(message);
    }
}

class PaymentException extends BusinessException {
    public PaymentException(String message) {
        super(message);
    }
}

class StockException extends BusinessException {
    public StockException(String message) {
        super(message);
    }
}

class SecurityException extends BusinessException {
    public SecurityException(String message) {
        super(message);
    }
}
