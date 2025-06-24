package com.henuka.imitations.config;

import jakarta.validation.Validator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;
import org.springframework.context.MessageSource;

@Configuration
public class ValidationConfig {

    @Bean
    public LocalValidatorFactoryBean validator(MessageSource messageSource) {
        LocalValidatorFactoryBean validatorFactory = new LocalValidatorFactoryBean();
        validatorFactory.setValidationMessageSource(messageSource);
        return validatorFactory;
    }

    @Bean
    public MethodValidationPostProcessor methodValidationPostProcessor(Validator validator) {
        MethodValidationPostProcessor processor = new MethodValidationPostProcessor();
        processor.setValidator(validator);
        return processor;
    }

    @Bean
    public ValidationService validationService(Validator validator) {
        return new ValidationService(validator);
    }
}

/**
 * Custom password validator
 */
@jakarta.validation.Constraint(validatedBy = PasswordValidator.class)
@java.lang.annotation.Target({java.lang.annotation.ElementType.FIELD})
@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@interface ValidPassword {
    String message() default "Password must be at least 8 characters long and contain at least one uppercase letter, one lowercase letter, one number and one special character";
    Class<?>[] groups() default {};
    Class<? extends jakarta.validation.Payload>[] payload() default {};
}

class PasswordValidator implements jakarta.validation.ConstraintValidator<ValidPassword, String> {
    private static final String PASSWORD_PATTERN = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=])(?=\\S+$).{8,}$";

    @Override
    public boolean isValid(String password, jakarta.validation.ConstraintValidatorContext context) {
        return password != null && password.matches(PASSWORD_PATTERN);
    }
}

/**
 * Custom email validator
 */
@jakarta.validation.Constraint(validatedBy = EmailValidator.class)
@java.lang.annotation.Target({java.lang.annotation.ElementType.FIELD})
@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@interface ValidEmail {
    String message() default "Invalid email address";
    Class<?>[] groups() default {};
    Class<? extends jakarta.validation.Payload>[] payload() default {};
}

class EmailValidator implements jakarta.validation.ConstraintValidator<ValidEmail, String> {
    private static final String EMAIL_PATTERN = "^[A-Za-z0-9+_.-]+@(.+)$";

    @Override
    public boolean isValid(String email, jakarta.validation.ConstraintValidatorContext context) {
        return email != null && email.matches(EMAIL_PATTERN);
    }
}

/**
 * Validation service
 */
@org.springframework.stereotype.Service
class ValidationService {
    private final Validator validator;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ValidationService.class);

    public ValidationService(Validator validator) {
        this.validator = validator;
    }

    public <T> java.util.Set<jakarta.validation.ConstraintViolation<T>> validate(T object) {
        try {
            return validator.validate(object);
        } catch (Exception e) {
            log.error("Validation failed", e);
            throw new ValidationException("Validation failed", e);
        }
    }

    public <T> void validateAndThrow(T object) {
        java.util.Set<jakarta.validation.ConstraintViolation<T>> violations = validate(object);
        if (!violations.isEmpty()) {
            throw new jakarta.validation.ConstraintViolationException(violations);
        }
    }
}

/**
 * Custom validation exception
 */
class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }

    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}

/**
 * Validation aspect for automatic validation
 */
@org.springframework.stereotype.Component
@org.aspectj.lang.annotation.Aspect
class ValidationAspect {
    private final ValidationService validationService;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ValidationAspect.class);

    public ValidationAspect(ValidationService validationService) {
        this.validationService = validationService;
    }

    @org.aspectj.lang.annotation.Before("@annotation(org.springframework.validation.annotation.Validated)")
    public void validateMethod(org.aspectj.lang.JoinPoint joinPoint) {
        try {
            for (Object arg : joinPoint.getArgs()) {
                if (arg != null) {
                    validationService.validateAndThrow(arg);
                }
            }
        } catch (Exception e) {
            log.error("Method validation failed", e);
            throw e;
        }
    }
}
