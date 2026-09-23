package com.andulf.aiden;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class Session {
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("aiden", Context.MODE_PRIVATE); }
    private static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        if (!ks.containsAlias("aiden_session")) {
            KeyGenerator gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            gen.init(new KeyGenParameterSpec.Builder("aiden_session", KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            gen.generateKey();
        }
        return (SecretKey)ks.getKey("aiden_session", null);
    }
    static void save(Context c, String token) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key());
        String value = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":" + Base64.encodeToString(cipher.doFinal(token.getBytes("UTF-8")), Base64.NO_WRAP);
        if (!prefs(c).edit().putString("session", value).commit()) throw new Exception("Could not save sign-in.");
    }
    static String token(Context c) throws Exception {
        String saved = prefs(c).getString("session", "");
        if (saved.isEmpty()) throw new LoginRequired("Sign in to your Fellow account.");
        try {
            String[] parts = saved.split(":"); Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
            return new String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), "UTF-8");
        } catch (Exception e) { clear(c); throw new LoginRequired("Please sign in again."); }
    }
    static void clear(Context c) { prefs(c).edit().remove("session").remove("device").remove("deviceName").apply(); }
    static class LoginRequired extends Exception { LoginRequired(String message) { super(message); } }
}
