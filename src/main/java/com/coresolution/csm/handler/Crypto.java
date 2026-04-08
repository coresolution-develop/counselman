package com.coresolution.csm.handler;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

public final class Crypto {
    private static final String ALG = "AES";
    private static final String TRANSFORM = "AES/ECB/PKCS5Padding";
    // 환경변수(또는 설정)에서 키를 읽어오세요. 예: 16/24/32바이트 또는 임의 문자열
    private static final SecretKeySpec KEY = loadKey();

    private Crypto() {
    }

    public static byte[] encryptToBytes(String plainText) {
        if (plainText == null)
            return null;
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORM);
            cipher.init(Cipher.ENCRYPT_MODE, KEY);
            return cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("AES encrypt ECB failed", e);
        }
    }

    public static String decryptToString(byte[] blob) {
        if (blob == null)
            return null;
        try {
            // 1) 기본 가정: blob은 순수 AES 암호문 바이트
            byte[] cipherBytes = blob;

            // 2) 호환 처리: 과거에 BLOB에 Base64 문자열/Hex 문자열이 들어갔던 경우
            if (!isBlockAligned(cipherBytes)) {
                if (looksLikeBase64(cipherBytes)) {
                    cipherBytes = Base64.getDecoder().decode(cipherBytes);
                } else if (looksLikeHex(cipherBytes)) {
                    cipherBytes = hexToBytes(new String(blob, StandardCharsets.US_ASCII));
                }
            }

            Cipher cipher = Cipher.getInstance(TRANSFORM);
            cipher.init(Cipher.DECRYPT_MODE, KEY);
            byte[] plain = cipher.doFinal(cipherBytes);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("AES decrypt ECB failed", e);
        }
    }

    // --- Key loading / normalization ---

    private static SecretKeySpec loadKey() {
        try {
            // 1) 키 원본 확보: 환경변수, yml, 인스턴스별 키 등에서 가져오세요.
            // 예시: CSM_AES_KEY (hex / base64 / 평문 모두 허용)
            String keySource = System.getenv("CSM_AES_KEY");
            if (keySource == null || keySource.isEmpty()) {
                // 임시: 로컬 개발 기본키 (반드시 운영에서는 제거/교체)
                keySource = "local-dev-default-key";
            }

            byte[] keyBytes = decodeKeyMaterial(keySource);
            byte[] normalized = normalizeToAesKey(keyBytes); // 16/24/32 바이트로 보정
            return new SecretKeySpec(normalized, ALG);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize AES key", e);
        }
    }

    private static byte[] decodeKeyMaterial(String src) throws Exception {
        // Base64로 보이는 경우
        if (src.matches("^[A-Za-z0-9+/=\\r\\n]+$") && src.length() % 4 == 0) {
            try {
                return Base64.getDecoder().decode(src);
            } catch (IllegalArgumentException ignore) {
            }
        }
        // Hex로 보이는 경우
        if (src.matches("^[0-9A-Fa-f]+$") && (src.length() % 2 == 0)) {
            return hexToBytes(src);
        }
        // 그 외: 평문 문자열
        return src.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] normalizeToAesKey(byte[] raw) throws Exception {
        // 이미 16/24/32면 그대로 사용
        if (raw.length == 16 || raw.length == 24 || raw.length == 32)
            return raw;
        // 그 외엔 SHA-256으로 해시 후 16바이트(128비트) 사용 (필요시 32바이트도 가능)
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw);
        return Arrays.copyOf(digest, 16);
    }

    // --- Helpers ---

    private static boolean isBlockAligned(byte[] arr) {
        return arr.length > 0 && (arr.length % 16 == 0);
    }

    private static boolean looksLikeBase64(byte[] arr) {
        // 대부분 ASCII 범위이며 Base64 문자셋일 때만 true
        int len = arr.length;
        if (len == 0 || (len % 4 != 0))
            return false;
        for (byte b : arr) {
            int c = b & 0xFF;
            boolean ok = (c >= 'A' && c <= 'Z') ||
                    (c >= 'a' && c <= 'z') ||
                    (c >= '0' && c <= '9') ||
                    c == '+' || c == '/' || c == '=' || c == '\r' || c == '\n';
            if (!ok)
                return false;
        }
        return true;
    }

    private static boolean looksLikeHex(byte[] arr) {
        if (arr.length == 0 || (arr.length % 2 != 0))
            return false;
        for (byte b : arr) {
            int c = b & 0xFF;
            boolean ok = (c >= '0' && c <= '9') ||
                    (c >= 'A' && c <= 'F') ||
                    (c >= 'a' && c <= 'f');
            if (!ok)
                return false;
        }
        return true;
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return out;
    }
}