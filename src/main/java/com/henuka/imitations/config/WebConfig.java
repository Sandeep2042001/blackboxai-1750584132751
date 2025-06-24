package com.henuka.imitations.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;
import java.util.Locale;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * Configure resource handlers for static resources
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Static resources
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/")
                .setCachePeriod(3600);

        // Product images
        registry.addResourceHandler("/images/products/**")
                .addResourceLocations("file:uploads/products/")
                .setCachePeriod(3600);

        // Webjars
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/")
                .setCachePeriod(3600);
    }

    /**
     * Configure view controllers for simple mappings
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/login").setViewName("auth/login");
        registry.addViewController("/register").setViewName("auth/register");
        registry.addViewController("/forgot-password").setViewName("auth/forgot-password");
        registry.addViewController("/reset-password").setViewName("auth/reset-password");
    }

    /**
     * Configure locale resolver for internationalization
     */
    @Bean
    public LocaleResolver localeResolver() {
        SessionLocaleResolver resolver = new SessionLocaleResolver();
        resolver.setDefaultLocale(new Locale("en"));
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

    /**
     * Add interceptors to the registry
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Locale change interceptor
        registry.addInterceptor(localeChangeInterceptor());

        // Cart interceptor to maintain cart information across requests
        registry.addInterceptor(cartInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns("/static/**", "/webjars/**", "/images/**");

        // Security interceptor for admin pages
        registry.addInterceptor(adminSecurityInterceptor())
                .addPathPatterns("/admin/**");
    }

    /**
     * Configure custom formatters and converters
     */
    @Override
    public void addFormatters(FormatterRegistry registry) {
        // Add custom formatters for date, currency, etc.
        registry.addFormatter(new org.springframework.format.datetime.standard.DateTimeFormatterRegistrar() {
            @Override
            public void registerFormatters(FormatterRegistry registry) {
                this.addDateTimeFormatters(registry);
            }
        }.getFormatter());
    }

    /**
     * Cart interceptor to maintain cart information
     */
    @Bean
    public CartInterceptor cartInterceptor() {
        return new CartInterceptor();
    }

    /**
     * Admin security interceptor
     */
    @Bean
    public AdminSecurityInterceptor adminSecurityInterceptor() {
        return new AdminSecurityInterceptor();
    }
}

/**
 * Custom interceptor to handle cart information
 */
class CartInterceptor implements org.springframework.web.servlet.HandlerInterceptor {
    @Override
    public boolean preHandle(jakarta.servlet.http.HttpServletRequest request,
                           jakarta.servlet.http.HttpServletResponse response,
                           Object handler) {
        String cartId = (String) request.getSession().getAttribute("cartSessionId");
        if (cartId == null) {
            cartId = java.util.UUID.randomUUID().toString();
            request.getSession().setAttribute("cartSessionId", cartId);
        }
        return true;
    }
}

/**
 * Custom interceptor for admin security
 */
class AdminSecurityInterceptor implements org.springframework.web.servlet.HandlerInterceptor {
    @Override
    public boolean preHandle(jakarta.servlet.http.HttpServletRequest request,
                           jakarta.servlet.http.HttpServletResponse response,
                           Object handler) throws Exception {
        org.springframework.security.core.Authentication auth = 
            org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        
        if (auth == null || !auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            response.sendRedirect("/login");
            return false;
        }
        return true;
    }
}
