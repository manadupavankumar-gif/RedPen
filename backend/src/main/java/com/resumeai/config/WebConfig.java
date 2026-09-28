package com.resumeai.config;

import com.resumeai.security.AuthInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final String[] allowedOrigins;

    public WebConfig(AuthInterceptor authInterceptor,
                     @Value("${app.cors.allowed-origins:*}") String[] allowedOrigins) {
        this.authInterceptor = authInterceptor;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // /api/auth/me needs a token (it reports who the token belongs to);
        // /api/builder/** is the new resume-builder, protected the same way /api/resume/** is.
        registry.addInterceptor(authInterceptor).addPathPatterns("/api/resume/**", "/api/builder/**", "/api/auth/me");
    }

    /** Lets the separate frontend (Live Server, npx serve, file://, etc.) call the API. */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
