package com.coresolution.csm.util;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.hibernate.annotations.Comment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AES128 {
    private final SecretKeySpec keySpec;
    private final byte[] ivBytes; // 레거시: key 앞 16바이트를 IV로 사용(고정 IV)
    private final String key; // 필요시 참조

    public AES128(@Value("${login.aes-key}") String key) {
        if (key == null || key.length() < 16) {
            throw new IllegalArgumentException("AES128 key must be at least 16 characters.");
        }
        this.key = key;

        // 키 16바이트로 패딩/절단
        byte[] keyBytes = Arrays.copyOf(key.getBytes(StandardCharsets.UTF_8), 16);
        this.keySpec = new SecretKeySpec(keyBytes, "AES");

        // 레거시 호환: 고정 IV = key 앞 16글자의 UTF-8 바이트
        this.ivBytes = key.substring(0, 16).getBytes(StandardCharsets.UTF_8);
    }

    /*
     * ======================
     * CBC (레거시: 고정 IV)
     * ======================
     */

    /** 평문 → CBC 암호문(Base64) */
    public String encrypt(String plain) { // 기존 encrypt 대체 (Base64)
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new IvParameterSpec(ivBytes));
            byte[] enc = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(enc);
        } catch (Exception e) {
            throw new RuntimeException("AES encrypt failed", e);
        }
    }

    /** CBC 암호문(Base64) → 평문 */
    public String decryptBase64(String b64Cipher) { // 기존 decrypt와 맞는 쌍
        try {
            byte[] cipherBytes = Base64.getDecoder().decode(b64Cipher);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(ivBytes));
            byte[] dec = cipher.doFinal(cipherBytes);
            return new String(dec, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("AES decrypt failed", e);
        }
    }

    /** 평문 → CBC 암호문(HEX) */
    public String encryptToHexCBC(String plain) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new IvParameterSpec(ivBytes));
            return bytesToHex(cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("AES encrypt HEX failed", e);
        }
    }

    /** CBC 암호문(HEX) → 평문 (이전 코드 decrypt 호환용) */
    public String decryptHexCBC(String hexCipher) {
        try {
            byte[] cipherBytes = hexToBytes(hexCipher);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(ivBytes));
            return new String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("AES decrypt HEX failed", e);
        }
    }

    /*
     * =============
     * ECB (권장X)
     * =============
     */

    public String encryptBase64ECB(String plain) {
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            return Base64.getEncoder().encodeToString(cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("AES encrypt ECB failed", e);
        }
    }

    public String decryptBase64ECB(String b64Cipher) {
        try {
            byte[] cipherBytes = Base64.getDecoder().decode(b64Cipher);
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            return new String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("AES decrypt ECB failed", e);
        }
    }

    /** ECB 암호문(HEX) → 평문 */
    public String decryptHexECB(String hexCipher) {
        try {
            byte[] cipherBytes = hexToBytes(hexCipher);
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            return new String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("AES decrypt HEX (ECB) failed", e);
        }
    }

    /** 평문 → ECB 암호문(HEX) */
    public String encryptHexECB(String plain) {
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            return bytesToHex(cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("AES encrypt HEX (ECB) failed", e);
        }
    }

    /** ✅ 호환용: 이전 코드의 decryptECB(...) 호출을 그대로 살리고 자동 판별 */
    public String decryptECB(String cipherText) {
        if (cipherText == null)
            return null;
        // HEX 로만 구성되었고 길이가 짝수면 HEX로 간주
        boolean looksHex = (cipherText.length() % 2 == 0) && cipherText.matches("(?i)[0-9a-f]+");
        if (looksHex) {
            return decryptHexECB(cipherText);
        }
        // 아니면 Base64 로 간주
        return decryptBase64ECB(cipherText);
    }

    /** ✅ 호환용: encryptECB(...)도 필요하면 추가 */
    public String encryptECB(String plain, boolean asHex) {
        return asHex ? encryptHexECB(plain) : encryptBase64ECB(plain);
    }
    /*
     * =========
     * Helpers
     * =========
     */

    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            String hex = Integer.toHexString(b & 0xff);
            if (hex.length() == 1)
                sb.append('0');
            sb.append(hex);
        }
        return sb.toString();
    }

    public static byte[] hexToBytes(String hex) {
        int len = hex.length();
        if ((len & 1) != 0)
            throw new IllegalArgumentException("Invalid hex length");
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return out;
    }
}
