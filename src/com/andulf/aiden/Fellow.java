package com.andulf.aiden;

import android.content.Context;
import org.json.*;
import java.net.*;
import java.io.*;
import java.util.TimeZone;

final class Fellow {
    private static final String API="https://l8qtmnc692.execute-api.us-west-2.amazonaws.com/v2";
    private static String call(Context c, String method, String path, JSONObject body, boolean auth) throws Exception {
        String token = auth ? Session.token(c) : null;
        HttpURLConnection conn=(HttpURLConnection)new URL(API+path).openConnection();
        try {
            conn.setRequestMethod(method); conn.setConnectTimeout(12000); conn.setReadTimeout(15000);
            conn.setUseCaches(false); conn.setRequestProperty("Cache-Control", "no-cache");
            conn.setInstanceFollowRedirects(false); conn.setRequestProperty("Content-Type", "application/json");
            if(auth)conn.setRequestProperty("Authorization", "Bearer "+token);
            if(body!=null){byte[] bytes=body.toString().getBytes("UTF-8");conn.setDoOutput(true);conn.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=conn.getOutputStream()){out.write(bytes);}}
            int code=conn.getResponseCode();
            if(code==401||code==403){Session.invalidateToken(c);throw new Session.LoginRequired(auth?"Session expired. Sign in again.":"Fellow rejected sign-in. Check your email and password.");}
            if(code<200||code>=300)throw new IOException("Fellow returned HTTP "+code+". Check the brewer. No command was retried.");
            try(InputStream in=conn.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>2000000)throw new IOException("Response too large");out.write(buffer,0,n);}return out.toString("UTF-8");
            }
        } finally { conn.disconnect(); }
    }
    static void login(Context c,String email,String password) throws Exception {
        JSONObject body=new JSONObject().put("email",email.trim()).put("password",password).put("timezone",TimeZone.getDefault().getID());
        JSONObject response=new JSONObject(call(c,"POST","/auth/login",body,false));
        String token=response.optString("accessToken","");if(token.isEmpty())throw new IOException("Fellow returned no session.");Session.save(c,token,email,password);
    }
    static JSONArray devices(Context c) throws Exception {
        try { return new JSONArray(call(c,"GET","/devices?dataType=real",null,true)); }
        catch (Session.LoginRequired expired) {
            String[] saved = Session.credentials(c);
            if (saved == null) throw expired;
            login(c, saved[0], saved[1]);
            // Retry only the read, once. Never replay a brew command.
            return new JSONArray(call(c,"GET","/devices?dataType=real",null,true));
        }
    }
    static JSONObject selected(Context c,JSONArray all) throws Exception {
        String id=Session.prefs(c).getString("device","");JSONObject only=null;int count=0;
        for(int i=0;i<all.length();i++){JSONObject d=all.getJSONObject(i);if(!d.optString("id").matches("FB_[a-zA-Z0-9-]+"))continue;only=d;count++;if(id.equals(d.optString("id")))return d;}
        if(count==1){Session.prefs(c).edit().putString("device",only.getString("id")).apply();return only;}
        throw new IOException(count==0?"No Aiden found on this account.":"Select your brewer in the app first.");
    }
    static synchronized JSONObject brew(Context c) throws Exception {
        long now=System.currentTimeMillis(),previous=Session.prefs(c).getLong("lastAttempt",0);
        if(previous>0&&now-previous<60000)throw new IOException("A start was attempted recently. Check your brewer before trying again.");
        JSONObject d=selected(c,devices(c));
        if(!d.optBoolean("isConnected"))throw new IOException("Brewer is offline.");
        if(BrewPolicy.active(d.optBoolean("brewing"),d.optLong("brewStartTime"),d.optLong("brewEndTime")))throw new IOException("The brewer reports that it is already brewing.");
        String profile=d.optString("ibSelectedProfileId","");double water=d.optDouble("ibWaterQuantity",0);
        if(profile.isEmpty()||!Double.isFinite(water)||water<1||water>1500)throw new IOException("Saved recipe or quantity is unavailable.");
        if(!Session.prefs(c).edit().putLong("lastAttempt",now).commit())throw new IOException("Could not record the start request.");
        JSONObject body=new JSONObject().put("profileId",profile).put("amountOfWater",(int)water);
        // Exactly one explicit-recipe PATCH. No automatic retry or flag changes.
        call(c,"PATCH","/devices/"+d.getString("id")+"/start",body,true);
        return d;
    }
}
