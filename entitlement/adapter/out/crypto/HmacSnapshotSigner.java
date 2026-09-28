package com.atlas.entitlement.adapter.out.crypto;

import com.atlas.entitlement.domain.model.EntitlementSnapshot;
import com.atlas.entitlement.domain.port.SnapshotSigner;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * HMAC-SHA256 signer.
 *
 * <p>Symmetric because both planes are operated by us and a shared secret is simpler to rotate
 * than a keypair. If the retrieval plane were ever run by a third party this would need to become
 * asymmetric — verification would then no longer imply the ability to forge.
 */
@Component
public class HmacSnapshotSigner implements SnapshotSigner {

    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] key;

    public HmacSnapshotSigner(@Value("${atlas.entitlement.signing-key}") String key) {
        if (key == null || key.length() < 32) {
            // Rejected at startup rather than at first use. A weak key discovered under load is
            // discovered by whoever is attacking it.
            throw new IllegalArgumentException(
                    "entitlement signing key must be at least 32 characters");
        }
        this.key = key.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    @Override
    public EntitlementSnapshot sign(EntitlementSnapshot unsigned) {
        return unsigned.withSignature(mac(unsigned.canonicalBytes()));
    }

    @Override
    public boolean verify(EntitlementSnapshot snapshot) {
        // Constant-time. A short-circuiting comparison leaks the signature one byte at a time to
        // anything able to measure response latency.
        return MessageDigest.isEqual(snapshot.signature(), mac(snapshot.canonicalBytes()));
    }

    private byte[] mac(byte[] payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            return mac.doFinal(payload);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("entitlement signing failed", e);
        }
    }
}
