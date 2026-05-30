package com.personal_tracker.app.service;

import com.personal_tracker.app.entity.FinanceOperation;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class OperationKeyBuilder {

    public String build(Long userId, FinanceOperation operation) {
        String rawKey = String.join("|",
                value(userId),
                value(operation.getOperationDate()),
                value(operation.getOperationAmount()),
                value(operation.getOperationCurrency()),
                value(operation.getCategory()),
                value(operation.getDescription()),
                value(operation.getSource())
        );

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 недоступен", exception);
        }
    }

    private String value(Object value) {
        return value == null ? "" : value.toString().trim().toLowerCase();
    }
}
