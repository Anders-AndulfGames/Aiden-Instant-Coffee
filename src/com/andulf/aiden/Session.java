package com.andulf.aiden;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONObject;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class Session {
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("aiden", Context.MODE_PRIVATE); }
    static boolean available(Context c) { return prefs(c).contains("session") || prefs(c).contains("credentials"); }
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
    private static String encrypt(String plain) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key());
        return Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":" + Base64.encodeToString(cipher.doFinal(plain.getBytes("UTF-8")), Base64.NO_WRAP);
    }
    private static String decrypt(String saved) throws Exception {
        String[] parts = saved.split(":");
        if (parts.length != 2) throw new Exception("Invalid saved sign-in");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), "UTF-8");
    }
    static void save(Context c, String token, String email, String password) throws Exception {
        String credentials = new JSONObject().put("email", email.trim()).put("password", password).toString();
        String savedToken = encrypt(token), savedCredentials = encrypt(credentials);
        if (!prefs(c).edit().putString("session", savedToken).putString("credentials", savedCredentials).commit())
            throw new java.io.IOException("Could not save sign-in. Please try again.");
    }
    static String[] credentials(Context c) throws LoginRequired {
        String saved = prefs(c).getString("credentials", "");
        if (saved.isEmpty()) return null;
        try {
            JSONObject value = new JSONObject(decrypt(saved));
            return new String[]{value.getString("email"), value.getString("password")};
        } catch (Exception e) {
            prefs(c).edit().remove("credentials").apply();
            throw new LoginRequired("Saved login could not be read. Please sign in again.");
        }
    }
    static String token(Context c) throws LoginRequired {
        String saved = prefs(c).getString("session", "");
        if (saved.isEmpty()) throw new LoginRequired("Sign in to your Fellow account.");
        try { return decrypt(saved); }
        catch (Exception e) { invalidateToken(c); throw new LoginRequired("Please sign in again."); }
    }
    static void invalidateToken(Context c) { prefs(c).edit().remove("session").apply(); }
    static void clear(Context c) { prefs(c).edit().remove("session").remove("credentials").remove("device").remove("deviceName").apply(); }
    static class LoginRequired extends Exception { LoginRequired(String message) { super(message); } }
}