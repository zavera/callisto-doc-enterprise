package io.callistotech.enterprise.auth;

import io.callistotech.enterprise.persistence.ApiKeyEntity;
import io.callistotech.enterprise.persistence.ApiKeyRepository;
import io.callistotech.enterprise.persistence.ClientEntity;
import io.callistotech.enterprise.persistence.ClientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private final ClientRepository clientRepository;

    /**
     * Generates a new API key for the given client.
     * The plain key is returned ONCE and never stored — only the SHA-256 hash is persisted.
     *
     * @param clientId UUID of the client
     * @return plain text API key (show to the client once, then discard)
     */
    @Transactional
    public String generateKey(UUID clientId) {
        clientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Client not found: " + clientId));

        String plainKey = "cde_" + UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        String keyHash = sha256Hex(plainKey);

        ApiKeyEntity entity = new ApiKeyEntity();
        entity.setId(UUID.randomUUID());
        entity.setClientId(clientId);
        entity.setKeyHash(keyHash);
        entity.setActive(true);
        entity.setCreatedAt(Instant.now());

        apiKeyRepository.save(entity);
        log.info("Generated new API key for client [id={}]", clientId);
        return plainKey;
    }

    /**
     * Verifies a raw API key by hashing and looking up in DB.
     * Updates lastUsedAt on a successful hit.
     *
     * @param rawKey plain text API key from X-API-Key header
     * @return the associated ClientEntity if the key is valid and active, empty otherwise
     */
    @Transactional
    public Optional<ClientEntity> verifyKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return Optional.empty();
        }

        String keyHash = sha256Hex(rawKey);
        Optional<ApiKeyEntity> keyEntityOpt = apiKeyRepository.findByKeyHash(keyHash);

        if (keyEntityOpt.isEmpty() || !keyEntityOpt.get().isActive()) {
            return Optional.empty();
        }

        ApiKeyEntity keyEntity = keyEntityOpt.get();
        keyEntity.setLastUsedAt(Instant.now());
        apiKeyRepository.save(keyEntity);

        return clientRepository.findById(keyEntity.getClientId());
    }

    /**
     * Computes SHA-256 hex of the given plain text.
     */
    public static String sha256Hex(String plain) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(plain.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
