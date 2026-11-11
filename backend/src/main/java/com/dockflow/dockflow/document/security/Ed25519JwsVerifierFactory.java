package com.dockflow.dockflow.document.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.Ed25519Verifier;
import com.nimbusds.jose.jca.JCAContext;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerifierFactory;
import com.nimbusds.jose.proc.SecurityContext;

import java.security.Key;
import java.util.List;
import java.util.Set;

final class Ed25519JwsVerifierFactory implements JWSVerifierFactory {

    private static final Set<JWSAlgorithm> ED25519_ALGORITHMS = Set.of(
        JWSAlgorithm.EdDSA,
        JWSAlgorithm.Ed25519
    );

    private final JCAContext jcaContext = new JCAContext();

    @Override
    public Set<JWSAlgorithm> supportedJWSAlgorithms() {
        return ED25519_ALGORITHMS;
    }

    @Override
    public JCAContext getJCAContext() {
        return jcaContext;
    }

    @Override
    public JWSVerifier createJWSVerifier(JWSHeader header, Key key) throws JOSEException {
        if (!ED25519_ALGORITHMS.contains(header.getAlgorithm())
            || !(key instanceof Ed25519JwkKey ed25519Key)) {
            throw new JOSEException("Ed25519 requires a matching Ed25519 JWK");
        }
        return new Ed25519Verifier(ed25519Key.jwk());
    }
}

final class Ed25519JwsKeySelector implements JWSKeySelector<SecurityContext> {

    private static final Set<JWSAlgorithm> ED25519_ALGORITHMS = Set.of(
        JWSAlgorithm.EdDSA,
        JWSAlgorithm.Ed25519
    );

    private final JWKSource<SecurityContext> jwkSource;

    Ed25519JwsKeySelector(JWKSource<SecurityContext> jwkSource) {
        this.jwkSource = jwkSource;
    }

    @Override
    public List<? extends Key> selectJWSKeys(JWSHeader header, SecurityContext context)
        throws com.nimbusds.jose.KeySourceException {
        if (!ED25519_ALGORITHMS.contains(header.getAlgorithm())) {
            return List.of();
        }
        JWKMatcher matcher = JWKMatcher.forJWSHeader(header);
        List<JWK> matchingKeys = jwkSource.get(new JWKSelector(matcher), context);
        return matchingKeys.stream()
            .filter(OctetKeyPair.class::isInstance)
            .map(OctetKeyPair.class::cast)
            .filter(jwk -> Curve.Ed25519.equals(jwk.getCurve()))
            .map(Ed25519JwkKey::new)
            .toList();
    }
}

record Ed25519JwkKey(OctetKeyPair jwk) implements Key {

    @Override
    public String getAlgorithm() {
        return "Ed25519";
    }

    @Override
    public String getFormat() {
        return "JWK";
    }

    @Override
    public byte[] getEncoded() {
        return jwk.getDecodedX().clone();
    }
}
