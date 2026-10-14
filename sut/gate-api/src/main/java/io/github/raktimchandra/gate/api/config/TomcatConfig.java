package io.github.raktimchandra.gate.api.config;

import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Configuration;

/**
 * Tomcat rejects an encoded '/' (%2F) itself, with an HTML error page that bypasses the API's
 * error contract. Passing it through lets Spring Security's StrictHttpFirewall reject it
 * instead — the request is still refused, but with the same problem+json body as every
 * other 4xx (found by SecuritySmokeTest).
 */
@Configuration(proxyBeanMethods = false)
public class TomcatConfig implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        factory.addConnectorCustomizers(connector -> connector.setEncodedSolidusHandling("passthrough"));
    }
}
