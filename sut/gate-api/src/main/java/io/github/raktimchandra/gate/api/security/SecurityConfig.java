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

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain api(HttpSecurity http, ProblemWriter problems) throws Exception {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt ->
                List.of(new SimpleGrantedAuthority("ROLE_" + jwt.getClaimAsString(TokenService.CLAIM_ROLE))));

        http.csrf(csrf -> csrf.disable()) // stateless bearer tokens; no cookies are used
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h.contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; "
                                + "frame-ancestors 'none'")))
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
