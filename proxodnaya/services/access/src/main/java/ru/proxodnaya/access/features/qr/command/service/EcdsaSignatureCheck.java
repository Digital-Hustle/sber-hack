package ru.proxodnaya.access.features.qr.command.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.springframework.stereotype.Component;

import ru.proxodnaya.access.features.qr.api.SignatureCheck;

/**
 * {@link SignatureCheck} поверх {@link PublicKeyCodec} и {@link EcdsaVerifier}.
 */
@Component
public class EcdsaSignatureCheck implements SignatureCheck {

    private final PublicKeyCodec codec;
    private final EcdsaVerifier verifier;

    public EcdsaSignatureCheck(PublicKeyCodec codec, EcdsaVerifier verifier) {
        this.codec = codec;
        this.verifier = verifier;
    }

    @Override
    public String normalize(String publicKey) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(codec.toSpki(publicKey));
    }

    @Override
    public boolean verify(String publicKey, String message, String signature) {
        try {
            byte[] sig = Base64.getUrlDecoder().decode(signature.replace('+', '-').replace('/', '_').replace("=", ""));
            return verifier.verify(codec.toSpki(publicKey), message.getBytes(StandardCharsets.UTF_8), sig);
        } catch (IllegalArgumentException error) {
            return false;
        }
    }
}
