package com.server.smsforwarder;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class CryptoStore {
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "sms_forwarder_local_v1";
    private static final String DEDUP_KEY_ALIAS = "sms_forwarder_dedup_v1";
    private static final byte FORMAT_VERSION = 1;

    private CryptoStore() {
    }

    static String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return "";
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
            byte[] iv = cipher.getIV();
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer packed = ByteBuffer.allocate(2 + iv.length + ciphertext.length);
            packed.put(FORMAT_VERSION);
            packed.put((byte) iv.length);
            packed.put(iv);
            packed.put(ciphertext);
            return Base64.encodeToString(packed.array(), Base64.NO_WRAP);
        } catch (GeneralSecurityException | java.io.IOException e) {
            throw new IllegalStateException("无法加密本地敏感数据", e);
        }
    }

    static String decrypt(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return "";
        }
        try {
            ByteBuffer packed = ByteBuffer.wrap(Base64.decode(encoded, Base64.NO_WRAP));
            byte version = packed.get();
            int ivLength = packed.get() & 0xff;
            if (version != FORMAT_VERSION || ivLength < 12 || ivLength > 32 || packed.remaining() <= ivLength) {
                throw new GeneralSecurityException("密文格式无效");
            }
            byte[] iv = new byte[ivLength];
            packed.get(iv);
            byte[] ciphertext = new byte[packed.remaining()];
            packed.get(ciphertext);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | java.io.IOException | RuntimeException e) {
            throw new IllegalStateException("无法解密本地敏感数据", e);
        }
    }

    static byte[] hmac(byte[] value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(getOrCreateDedupKey());
            return mac.doFinal(value);
        } catch (GeneralSecurityException | java.io.IOException e) {
            throw new IllegalStateException("无法生成本地去重标识", e);
        }
    }

    private static SecretKey getOrCreateKey() throws GeneralSecurityException, java.io.IOException {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
        keyStore.load(null);
        java.security.Key existing = keyStore.getKey(KEY_ALIAS, null);
        if (existing instanceof SecretKey) {
            return (SecretKey) existing;
        }

        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }

    private static SecretKey getOrCreateDedupKey()
            throws GeneralSecurityException, java.io.IOException {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
        keyStore.load(null);
        java.security.Key existing = keyStore.getKey(DEDUP_KEY_ALIAS, null);
        if (existing instanceof SecretKey) {
            return (SecretKey) existing;
        }
        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_HMAC_SHA256, KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(
                DEDUP_KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build());
        return generator.generateKey();
    }
}
