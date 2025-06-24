package com.henuka.imitations;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import java.util.Locale;

@SpringBootApplication
@EnableCaching
@EnableScheduling
@EnableTransactionManagement
public class HenukaImitationsApplication implements WebMvcConfigurer {

    public static void main(String[] args) {
        SpringApplication.run(HenukaImitationsApplication.class, args);
    }

    /**
     * Configure the default locale resolver
     */
    @Bean
    public SessionLocaleResolver localeResolver() {
        SessionLocaleResolver resolver = new SessionLocaleResolver();
        resolver.setDefaultLocale(new Locale("en"));
        return resolver;
    }

    /**
     * Configure the locale change interceptor
     */
    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName("lang");
        return interceptor;
    }

    /**
     * Configure message source for internationalization
     */
    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        return messageSource;
    }

    /**
     * Add interceptors to the registry
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeChangeInterceptor());
    }

    /**
     * Configure CORS globally
     */
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(org.springframework.web.servlet.config.annotation.CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins("http://localhost:8000")
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(true)
                        .maxAge(3600);
            }
        };
    }

    /**
     * Configure custom error attributes
     */
    @Bean
    public org.springframework.boot.web.servlet.error.ErrorAttributes errorAttributes() {
        return new org.springframework.boot.web.servlet.error.DefaultErrorAttributes() {
            @Override
            public java.util.Map<String, Object> getErrorAttributes(
                    org.springframework.web.context.request.WebRequest webRequest,
                    org.springframework.boot.web.error.ErrorAttributeOptions options) {
                java.util.Map<String, Object> errorAttributes = super.getErrorAttributes(webRequest, options);
                errorAttributes.put("timestamp", new java.util.Date());
                return errorAttributes;
            }
        };
    }

    /**
     * Configure custom exception handler
     */
    @Bean
    public org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler exceptionHandler() {
        return new org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler() {
            @org.springframework.web.bind.annotation.ExceptionHandler(Exception.class)
            public org.springframework.http.ResponseEntity<Object> handleAllExceptions(
                    Exception ex, org.springframework.web.context.request.WebRequest request) {
                java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
                body.put("timestamp", new java.util.Date());
                body.put("message", ex.getMessage());
                return new org.springframework.http.ResponseEntity<>(body, org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
            }
        };
    }

    /**
     * Configure custom async executor
     */
    @Bean
    public org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor taskExecutor() {
        org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor executor = new org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("HenukaAsync-");
        executor.initialize();
        return executor;
    }

    /**
     * Configure custom scheduled task executor
     */
    @Bean
    public org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler taskScheduler() {
        org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler scheduler = new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("HenukaScheduler-");
        return scheduler;
    }
}
