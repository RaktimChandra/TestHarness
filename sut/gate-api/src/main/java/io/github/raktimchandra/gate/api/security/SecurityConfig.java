package io.github.raktimchandra.gate.api.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import io.github.raktimchandra.gate.api.config.GateProperties;
import io.github.raktimchandra.gate.api.web.ProblemWriter;
import io.github.raktimchandra.gate.domain.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.CrossOriginEmbedderPolicyHeaderWriter;
import org.springframework.security.web.header.writers.CrossOriginOpenerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.CrossOriginResourcePolicyHeaderWriter;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.web.firewall.RequestRejectedHandler;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
    private static final int MIN_SECRET_BYTES = 32;

    @Bean
    SecretKey jwtKey(GateProperties props) {
        byte[] secret = props.jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length == 0) {
            secret = new byte[MIN_SECRET_BYTES];
            new SecureRandom().nextBytes(secret);
            log.warn("GATE_JWT_SECRET not set: generated a random signing key; tokens will not survive a restart");
        } else if (secret.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("gate.jwt.secret must be at least " + MIN_SECRET_BYTES + " bytes");
        }
        return new SecretKeySpec(secret, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey key) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(TokenService.ISSUER));
        return decoder;
    }

    /**
     * Requests the firewall rejects (e.g. ';', encoded '%' or '..' in the path) get the same
     * problem+json error contract as every other 4xx instead of a bare container error page.
     */
    @Bean
    RequestRejectedHandler requestRejectedHandler(ProblemWriter problems) {
        return (req, res, ex) -> problems.write(req, res, ErrorCode.VALIDATION_FAILED,
                "Request rejected: the URL contains characters that are not allowed");
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Every directive without a default-src fallback is set explicitly (ZAP rule 10055). */
    static final String CSP = "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; "
            + "font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; "
            + "frame-ancestors 'none'";

    static final String PERMISSIONS_POLICY = "camera=(), microphone=(), geolocation=(), payment=(), usb=(), "
            + "accelerometer=(), gyroscope=(), magnetometer=()";

    private static final RequestMatcher SWAGGER_UI = new OrRequestMatcher(
            PathPatternRequestMatcher.withDefaults().matcher("/swagger-ui/**"),
            PathPatternRequestMatcher.withDefaults().matcher("/swagger-ui.html"));

    @Bean
    SecurityFilterChain api(HttpSecurity http, ProblemWriter problems) throws Exception {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt ->
                List.of(new SimpleGrantedAuthority("ROLE_" + jwt.getClaimAsString(TokenService.CLAIM_ROLE))));

        http.csrf(csrf -> csrf.disable()) // stateless bearer tokens; no cookies are used
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h
                        // The app needs nothing inline. Swagger UI sets style attributes, so only its
                        // pages allow inline styles (found by the ZAP baseline scan, security.yml).
                        .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                                new NegatedRequestMatcher(SWAGGER_UI),
                                new StaticHeadersWriter("Content-Security-Policy", CSP)))
                        .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(SWAGGER_UI,
                                new StaticHeadersWriter("Content-Security-Policy",
                                        CSP.replace("style-src 'self'", "style-src 'self' 'unsafe-inline'"))))
                        .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy", PERMISSIONS_POLICY))
                        .crossOriginOpenerPolicy(c -> c.policy(
                                CrossOriginOpenerPolicyHeaderWriter.CrossOriginOpenerPolicy.SAME_ORIGIN))
                        .crossOriginEmbedderPolicy(c -> c.policy(
                                CrossOriginEmbedderPolicyHeaderWriter.CrossOriginEmbedderPolicy.REQUIRE_CORP))
                        .crossOriginResourcePolicy(c -> c.policy(
                                CrossOriginResourcePolicyHeaderWriter.CrossOriginResourcePolicy.SAME_ORIGIN)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers("/api/test-support/**").permitAll() // guarded by TestSupportKeyFilter
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/slots").hasRole("CLERK")
                        .requestMatchers(HttpMethod.POST, "/api/bookings/*/check-in").hasRole("CLERK")
                        .requestMatchers(HttpMethod.POST, "/api/bookings").hasRole("DRIVER")
                        .requestMatchers("/api/**", "/actuator/**").authenticated()
                        .anyRequest().permitAll()) // static UI assets and SPA routes
                .oauth2ResourceServer(rs -> rs
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint((req, res, ex) ->
                                problems.write(req, res, ErrorCode.UNAUTHENTICATED, "Missing or invalid access token"))
                        .accessDeniedHandler((req, res, ex) ->
                                problems.write(req, res, ErrorCode.FORBIDDEN, "Your role cannot perform this action")))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                problems.write(req, res, ErrorCode.UNAUTHENTICATED, "Missing or invalid access token"))
                        .accessDeniedHandler((req, res, ex) ->
                                problems.write(req, res, ErrorCode.FORBIDDEN, "Your role cannot perform this action")));
        return http.build();
    }
}
