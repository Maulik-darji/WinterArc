package com.app.winterarc.data.auth;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Keeps the session encrypted at rest: AES-256-GCM with a key that never leaves the Android
 * Keystore. If the key is gone (restore to another device, cleared credentials) the stored value
 * is unreadable and the user simply signs in again.
 */
public final class KeystoreSessionStorage implements SessionStorage {
    private static final String ALIAS = "winterarc_session_key";
    private static final String KEY = "session";
    private static final int TAG_BITS = 128;

    private final SharedPreferences prefs;

    public KeystoreSessionStorage(Context context) {
        prefs = context.getSharedPreferences("winterarc_session", Context.MODE_PRIVATE);
    }

    @Nullable
    @Override
    public synchronized String read() {
        String stored = prefs.getString(KEY, null);
        if (stored == null) return null;
        try {
            byte[] blob = Base64.decode(stored, Base64.NO_WRAP);
            int ivLength = blob[0] & 0xFF;
            byte[] iv = Arrays.copyOfRange(blob, 1, 1 + ivLength);
            byte[] cipherText = Arrays.copyOfRange(blob, 1 + ivLength, blob.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | RuntimeException e) {
            clear();
            return null;
        }
    }

    @Override
    public synchronized void write(String value) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key());
            byte[] iv = cipher.getIV();
            byte[] cipherText = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] blob = new byte[1 + iv.length + cipherText.length];
            blob[0] = (byte) iv.length;
            System.arraycopy(iv, 0, blob, 1, iv.length);
            System.arraycopy(cipherText, 0, blob, 1 + iv.length, cipherText.length);
            prefs.edit().putString(KEY, Base64.encodeToString(blob, Base64.NO_WRAP)).apply();
        } catch (GeneralSecurityException e) {
            // Never fall back to plain text: without a working Keystore the session just isn't kept.
            clear();
        }
    }

    @Override
    public synchronized void clear() {
        prefs.edit().remove(KEY).apply();
    }

    private static SecretKey key() throws GeneralSecurityException {
        try {
            KeyStore store = KeyStore.getInstance("AndroidKeyStore");
            store.load(null);
            if (store.containsAlias(ALIAS)) return (SecretKey) store.getKey(ALIAS, null);
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build());
            return generator.generateKey();
        } catch (java.io.IOException e) {
            throw new GeneralSecurityException(e);
        }
    }
}
