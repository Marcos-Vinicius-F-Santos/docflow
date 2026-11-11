package com.dockflow.dockflow.document.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.Ed25519Signer;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigurationTest {

    private HttpServer jwksServer;

    @AfterEach
    void stopJwksServer() {
        if (jwksServer != null) {
            jwksServer.stop(0);
        }
    }

    @Test
    void decodesNeonAuthEd25519TokenFromRemoteJwks() throws Exception {
        OctetKeyPair signingKey = new OctetKeyPairGenerator(Curve.Ed25519)
            .keyID("neon-auth-test-key")
            .generate();
        String jwks = "{\"keys\":[" + signingKey.toPublicJWK().toJSONString() + "]}";
        String jwksUrl = startJwksServer(jwks);

        Instant now = Instant.now();
        SignedJWT signedJwt = new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.EdDSA)
                .keyID(signingKey.getKeyID())
                .build(),
            new JWTClaimsSet.Builder()
                .subject("neon-user-id")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(900)))
                .build()
        );
        signedJwt.sign(new Ed25519Signer(signingKey));

        JwtDecoder decoder = new SecurityConfiguration().jwtDecoder(jwksUrl);
        Jwt decoded = decoder.decode(signedJwt.serialize());

        assertThat(decoded.getSubject()).isEqualTo("neon-user-id");
    }

    private String startJwksServer(String jwks) throws Exception {
        byte[] response = jwks.getBytes(StandardCharsets.UTF_8);
        jwksServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        jwksServer.createContext("/.well-known/jwks.json", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var body = exchange.getResponseBody()) {
                body.write(response);
            }
        });
        jwksServer.start();
        return "http://127.0.0.1:" + jwksServer.getAddress().getPort() + "/.well-known/jwks.json";
    }
}
