package com.henuka.imitations.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import java.util.Locale;

@Configuration
public class I18nConfig {

    /**
     * Configure message source
     */
    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasenames("messages", "errors");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(true);
        return messageSource;
    }

    /**
     * Configure locale resolver
     */
    @Bean
    public LocaleResolver localeResolver() {
        CookieLocaleResolver resolver = new CookieLocaleResolver();
        resolver.setDefaultLocale(Locale.ENGLISH);
        resolver.setCookieName("LOCALE");
        resolver.setCookieMaxAge(4800);
        return resolver;
    }

    /**
     * Configure locale change interceptor
     */
    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName("lang");
        return interceptor;
    }
}

/**
 * Internationalization service
 */
@org.springframework.stereotype.Service
class I18nService {
    
    private final MessageSource messageSource;
    private final I18nMetrics i18nMetrics;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(I18nService.class);

    public I18nService(MessageSource messageSource,
                      I18nMetrics i18nMetrics) {
        this.messageSource = messageSource;
        this.i18nMetrics = i18nMetrics;
    }

    /**
     * Get message
     */
    public String getMessage(String code, Object[] args, Locale locale) {
        try {
            long startTime = System.currentTimeMillis();
            
            String message = messageSource.getMessage(code, args, locale);
            
            long duration = System.currentTimeMillis() - startTime;
            i18nMetrics.recordMessageResolution(code, locale.toString(), duration);
            
            return message;
        } catch (org.springframework.context.NoSuchMessageException e) {
            i18nMetrics.recordMissingMessage(code, locale.toString());
            log.warn("Message not found: {} for locale: {}", code, locale);
            return code;
        } catch (Exception e) {
            i18nMetrics.recordError("getMessage");
            log.error("Failed to get message: {} for locale: {}", code, locale, e);
            throw new I18nException("Failed to get message", e);
        }
    }

    /**
     * Get all messages for locale
     */
    public java.util.Map<String, String> getAllMessages(Locale locale) {
        try {
            long startTime = System.currentTimeMillis();
            
            java.util.Map<String, String> messages = new java.util.HashMap<>();
            org.springframework.context.support.ResourceBundleMessageSource source = 
                (org.springframework.context.support.ResourceBundleMessageSource) messageSource;
            
            java.util.ResourceBundle bundle = source.getResourceBundle("messages", locale);
            java.util.Enumeration<String> keys = bundle.getKeys();
            
            while (keys.hasMoreElements()) {
                String key = keys.nextElement();
                messages.put(key, bundle.getString(key));
            }
            
            long duration = System.currentTimeMillis() - startTime;
            i18nMetrics.recordBulkResolution(locale.toString(), duration);
            
            return messages;
        } catch (Exception e) {
            i18nMetrics.recordError("getAllMessages");
            log.error("Failed to get all messages for locale: {}", locale, e);
            throw new I18nException("Failed to get all messages", e);
        }
    }
}

/**
 * Message resolver aspect
 */
@org.springframework.stereotype.Component
@org.aspectj.lang.annotation.Aspect
class MessageResolverAspect {
    
    private final I18nService i18nService;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MessageResolverAspect.class);

    public MessageResolverAspect(I18nService i18nService) {
        this.i18nService = i18nService;
    }

    @org.aspectj.lang.annotation.Around("@annotation(com.henuka.imitations.annotation.Localized)")
    public Object resolveMessage(org.aspectj.lang.ProceedingJoinPoint joinPoint) throws Throwable {
        try {
            Object result = joinPoint.proceed();
            
            if (result instanceof String) {
                return i18nService.getMessage(
                    (String) result,
                    new Object[]{},
                    org.springframework.context.i18n.LocaleContextHolder.getLocale()
                );
            }
            
            return result;
        } catch (Exception e) {
            log.error("Failed to resolve localized message", e);
            throw e;
        }
    }
}

/**
 * I18n exception
 */
class I18nException extends RuntimeException {
    
    public I18nException(String message) {
        super(message);
    }

    public I18nException(String message, Throwable cause) {
        super(message, cause);
    }
}

/**
 * I18n metrics
 */
@org.springframework.stereotype.Component
class I18nMetrics {
    
    private final io.micrometer.core.instrument.MeterRegistry registry;

    public I18nMetrics(io.micrometer.core.instrument.MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordMessageResolution(String code, String locale, long duration) {
        registry.timer("i18n.message.resolution",
            "code", code,
            "locale", locale)
            .record(java.time.Duration.ofMillis(duration));
    }

    public void recordMissingMessage(String code, String locale) {
        registry.counter("i18n.message.missing",
            "code", code,
            "locale", locale).increment();
    }

    public void recordBulkResolution(String locale, long duration) {
        registry.timer("i18n.bulk.resolution",
            "locale", locale)
            .record(java.time.Duration.ofMillis(duration));
    }

    public void recordError(String operation) {
        registry.counter("i18n.error",
            "operation", operation).increment();
    }
}
