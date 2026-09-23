package com.andulf.aiden;

import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.os.Bundle;

public class SessionCheck extends Instrumentation {
    private int checks;
    private void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
        checks++;
    }
    private Context isolatedContext() {
        return new ContextWrapper(getTargetContext()) {
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return super.getSharedPreferences("aiden_auth_test", mode);
            }
        };
    }
    private static final java.util.ArrayDeque<FakeConnection> replies = new java.util.ArrayDeque<>();
    private static final java.util.ArrayList<FakeConnection> requests = new java.util.ArrayList<>();
    private static class FakeConnection extends java.net.HttpURLConnection {
        final int code;
        final String body;
        final java.io.ByteArrayOutputStream sent = new java.io.ByteArrayOutputStream();
        FakeConnection(int code, String body) { super(null); this.code=code; this.body=body; }
        public void setRequestMethod(String value) { method=value; }
        public void connect() {}
        public void disconnect() {}
        public boolean usingProxy() { return false; }
        public int getResponseCode() { return code; }
        public java.io.OutputStream getOutputStream() { return sent; }
        public java.io.InputStream getInputStream() throws java.io.IOException { return new java.io.ByteArrayInputStream(body.getBytes("UTF-8")); }
    }
    private void checkRecovery(Context c) throws Exception {
        java.net.URL.setURLStreamHandlerFactory(protocol -> "https".equals(protocol) ? new java.net.URLStreamHandler() {
            protected java.net.URLConnection openConnection(java.net.URL url) throws java.io.IOException {
                FakeConnection next=replies.poll();
                if(next==null)throw new java.io.IOException("Unexpected request in authentication test");
                requests.add(next);
                return next;
            }
        } : null);
        Session.save(c,"expired","coffee@example.invalid","dummy-password");
        replies.add(new FakeConnection(401,""));
        replies.add(new FakeConnection(200,"{\"accessToken\":\"renewed\"}"));
        replies.add(new FakeConnection(200,"[]"));
        check(Fellow.devices(c).length()==0 && requests.size()==3, "expired read renews login and retries once");
        check("GET".equals(requests.get(0).getRequestMethod()) && "POST".equals(requests.get(1).getRequestMethod()) && "GET".equals(requests.get(2).getRequestMethod()), "renewal request sequence");
        check("Bearer renewed".equals(requests.get(2).getRequestProperty("Authorization")), "retry uses renewed token");
        org.json.JSONObject login=new org.json.JSONObject(requests.get(1).sent.toString("UTF-8"));
        check("coffee@example.invalid".equals(login.getString("email")) && "dummy-password".equals(login.getString("password")), "renewal uses saved credentials");
        requests.clear();
        Session.invalidateToken(c);
        replies.add(new FakeConnection(200,"{\"accessToken\":\"renewed-again\"}"));
        replies.add(new FakeConnection(200,"[]"));
        // Token lookup happens before opening a request so a missing token cannot consume a response.
        check(Fellow.devices(c).length()==0 && requests.size()==2, "missing token renews saved login");
        requests.clear();
        replies.add(new FakeConnection(401,""));
        replies.add(new FakeConnection(200,"{\"accessToken\":\"still-rejected\"}"));
        replies.add(new FakeConnection(401,""));
        try { Fellow.devices(c); throw new AssertionError("repeated rejection accepted"); }
        catch(Session.LoginRequired expected) { check(requests.size()==3, "renewal stops after one retry"); }
        requests.clear();
        replies.add(new FakeConnection(401,""));
        try { Fellow.devices(c); throw new AssertionError("bad saved password accepted"); }
        catch(Session.LoginRequired expected) { check(requests.size()==1 && Session.credentials(c)!=null, "rejected login prompts correction without looping"); }
        requests.clear();
        Session.save(c,"valid","coffee@example.invalid","dummy-password");
        Session.prefs(c).edit().remove("lastAttempt").commit();
        replies.add(new FakeConnection(200,"[{\"id\":\"FB_test\",\"isConnected\":true,\"ibSelectedProfileId\":\"test\",\"ibWaterQuantity\":300}]"));
        replies.add(new FakeConnection(401,""));
        try { Fellow.brew(c); throw new AssertionError("rejected brew accepted"); }
        catch(Session.LoginRequired expected) { check(requests.size()==2 && "PATCH".equals(requests.get(1).getRequestMethod()), "brew command is never retried or renewed after rejection"); }
    }
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        Context c = isolatedContext();
        Bundle result = new Bundle();
        int code = -1;
        try {
            Session.prefs(c).edit().clear().commit();
            check(!Session.available(c), "fresh install requires sign-in");
            Session.save(c, "dummy-token", " coffee@example.invalid ", " dummy-password : å ");
            String raw = Session.prefs(c).getString("credentials", "");
            check(!raw.contains("coffee@example.invalid") && !raw.contains("dummy-password"), "credentials encrypted at rest");
            check(!Session.prefs(c).getString("session", "").contains("dummy-token"), "token encrypted at rest");
            Context reopened = isolatedContext();
            check("dummy-token".equals(Session.token(reopened)), "saved token can be reopened");
            check("coffee@example.invalid".equals(Session.credentials(reopened)[0]), "email persists and is trimmed");
            check(" dummy-password : å ".equals(Session.credentials(reopened)[1]), "password persists exactly");
            Session.invalidateToken(c);
            check(Session.available(c) && Session.credentials(c) != null, "expired token retains saved login");
            Session.save(c, "renewed-token", "coffee@example.invalid", " dummy-password : å ");
            check("renewed-token".equals(Session.token(c)), "renewal replaces token");
            check(!raw.equals(Session.prefs(c).getString("credentials", "")), "fresh encryption uses a new IV");
            Session.prefs(c).edit().putString("session", "corrupted").commit();
            try { Session.token(c); throw new AssertionError("corrupt token accepted"); }
            catch (Session.LoginRequired expected) { check(Session.credentials(c) != null, "corrupt token preserves credentials for recovery"); }
            Session.prefs(c).edit().putString("credentials", "corrupted").commit();
            try { Session.credentials(c); throw new AssertionError("corrupt credentials accepted"); }
            catch (Session.LoginRequired expected) { check(!Session.prefs(c).contains("credentials"), "unreadable credentials prompt sign-in"); }
            Session.save(c, "dummy-token", "coffee@example.invalid", "dummy-password");
            Session.prefs(c).edit().putString("device", "dummy-device").putLong("lastAttempt", 123).commit();
            Session.clear(c);
            check(!Session.available(c) && Session.credentials(c) == null && !Session.prefs(c).contains("device"), "sign-out removes saved login and selection");
            check(Session.prefs(c).getLong("lastAttempt", 0) == 123, "sign-out preserves brew cooldown");
            checkRecovery(c);
            result.putString("stream", "PASS: " + checks + " Android Keystore and saved-login checks\n");
        } catch (Throwable failure) {
            code = 0;
            result.putString("stream", "FAIL: " + failure.getClass().getSimpleName() + ": " + failure.getMessage() + "\n");
        } finally {
            Session.prefs(c).edit().clear().commit();
        }
        finish(code, result);
    }
}