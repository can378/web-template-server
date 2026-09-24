package com.example.webtemplate.global.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import jakarta.servlet.http.HttpServletRequest;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springdoc.webmvc.ui.SwaggerIndexPageTransformer;
import org.springdoc.webmvc.ui.SwaggerWelcomeCommon;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.resource.ResourceTransformerChain;
import org.springframework.web.servlet.resource.TransformedResource;

@Component
public class SwaggerCsrfTransformer extends SwaggerIndexPageTransformer {
    public SwaggerCsrfTransformer(SwaggerUiConfigProperties config, SwaggerUiOAuthProperties oauth,
            SwaggerWelcomeCommon welcome, ObjectMapperProvider mapper) {
        super(config, oauth, welcome, mapper);
    }

    @Override
    public Resource transform(HttpServletRequest request, Resource resource, ResourceTransformerChain chain)
            throws IOException {
        Resource transformed = super.transform(request, resource, chain);
        if (!"swagger-initializer.js".equals(resource.getFilename())) return transformed;
        String script = new ClassPathResource("swagger/request-interceptor.js")
                .getContentAsString(StandardCharsets.UTF_8);
        String initializer = transformed.getContentAsString(StandardCharsets.UTF_8)
                .replace("SwaggerUIBundle({", "SwaggerUIBundle({\nrequestInterceptor: " + script + ",\n");
        return new TransformedResource(transformed, initializer.getBytes(StandardCharsets.UTF_8));
    }
}
