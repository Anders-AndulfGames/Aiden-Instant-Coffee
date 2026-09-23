package com.andulf.aiden;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.graphics.Color;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private static final ExecutorService IO=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private LinearLayout layout,lights;
    private TextView status,recipe,stage,countdown;
    private long lastStatusAt=0, observedBrewStart=-1;
    private final Runnable tick=new Runnable(){public void run(){updateCountdown();if(visible)main.postDelayed(this,1000);}};
    private Button brew;
    private boolean working=false,visible=false,starting=false,pendingWidgetStart=false;
    private JSONObject device;
    private final Runnable poll=()->refresh();
    private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n);}
    private TextView text(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(Color.rgb(37,44,40));t.setPadding(0,dp(8),0,dp(8));layout.addView(t);return t;}
    private Button button(String label,Runnable action){Button b=new Button(this);b.setText(label);b.setAllCaps(false);layout.addView(b,new LinearLayout.LayoutParams(-1,dp(56)));b.setOnClickListener(v->action.run());return b;}
    private void base(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Color.rgb(238,234,226));
        layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(dp(26),dp(32),dp(26),dp(28));scroll.addView(layout);setContentView(scroll);
        text("AIDEN / COFFEE",13);text("Fellow Aiden Instant Brew",30);
    }
    @Override public void onCreate(Bundle saved){super.onCreate(saved);getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,WindowManager.LayoutParams.FLAG_SECURE);
        // Do not re-execute the widget action after rotation or process recreation.
        boolean requested=saved==null&&getClass()==WidgetActivity.class&&"com.andulf.aiden.WIDGET_BREW".equals(getIntent().getAction());
        getIntent().setAction(null);
        if(!Session.prefs(this).contains("session")){showLogin(requested?"Sign in, then tap Brew when ready.":"");}
        else {showBrewer();if(requested)requestWidgetStart();}
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);boolean requested=getClass()==WidgetActivity.class&&"com.andulf.aiden.WIDGET_BREW".equals(intent.getAction());intent.setAction(null);if(requested)requestWidgetStart();}
    private void requestWidgetStart(){
        if(starting)return;
        if(!Session.prefs(this).contains("session")){pendingWidgetStart=false;showLogin("Sign in, then tap Start instant brew when ready.");return;}
        if(working){pendingWidgetStart=true;return;}
        pendingWidgetStart=false;startBrew();
    }
    @Override protected void onResume(){super.onResume();visible=true;main.removeCallbacks(tick);main.post(tick);if(Session.prefs(this).contains("session")&&!working)refresh();}
    @Override protected void onPause(){visible=false;main.removeCallbacks(poll);main.removeCallbacks(tick);super.onPause();}
    private void showLogin(String message){
        main.removeCallbacks(poll);device=null;countdown=null;base();text("Connect your Fellow account",20);
        EditText email=new EditText(this);email.setHint("Email");email.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);layout.addView(email);
        EditText password=new EditText(this);password.setHint("Password");password.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);layout.addView(password);
        Button sign=button("Sign in",()->{});status=text(message,15);
        text("Your password is not saved. Your session is encrypted using Android Keystore. The app connects directly to Fellow.",13);
        sign.setOnClickListener(v->{if(working)return;String e=email.getText().toString(),p=password.getText().toString();if(e.trim().isEmpty()||p.isEmpty()){status.setText("Enter your email and password.");return;}password.setText("");working=true;sign.setEnabled(false);status.setText("Signing in...");
            IO.execute(()->{try{Fellow.login(getApplicationContext(),e,p);main.post(()->{working=false;if(isDestroyed())return;showBrewer();refresh();BrewWidget.updateAll(this);});}catch(Exception ex){main.post(()->{working=false;if(isDestroyed())return;sign.setEnabled(true);status.setText(message(ex));});}});
        });
    }
    private void showBrewer(){
        base();text("Prepare water, a filter and coffee before starting.",16);recipe=text("Instant Brew · loading saved quantity...",22);
        stage=text("Checking brewer...",20);countdown=text("",30);countdown.setVisibility(View.GONE);lights=new LinearLayout(this);lights.setOrientation(LinearLayout.VERTICAL);layout.addView(lights);
        brew=button("Start instant brew",()->startBrew());brew.setEnabled(false);status=text("",15);
        button("Choose brewer",()->chooseBrewer());
        button("Sign out",()->{if(working){status.setText("Wait for the current request to finish.");return;}Session.clear(this);device=null;BrewWidget.updateAll(this);showLogin("");});
        text("Uses the recipe and quantity saved for Instant Brew. No PC, USB or Bluetooth required.",13);
    }
    private String message(Exception e){return e instanceof java.net.SocketTimeoutException?"Connection timed out. Check the brewer before trying again; no command was retried.":e instanceof java.io.IOException?e.getMessage():e instanceof Session.LoginRequired?e.getMessage():"Unable to complete the request. Check your connection and try again.";}
    private void failed(Exception e){if(isDestroyed())return;lastStatusAt=0;updateCountdown();if(e instanceof Session.LoginRequired){BrewWidget.updateAll(this);showLogin(message(e));}else{status.setText(message(e));stage.setText("Status unavailable");if(lights!=null)lights.removeAllViews();if(brew!=null)brew.setEnabled(false);}}
    private void schedule(){main.removeCallbacks(poll);if(visible&&pendingWidgetStart&&!working){requestWidgetStart();return;}if(visible&&Session.prefs(this).contains("session"))main.postDelayed(poll,5000);}
    private void refresh(){if(!Session.prefs(this).contains("session"))return;if(working){schedule();return;}working=true;
        IO.execute(()->{try{JSONObject d=Fellow.selected(getApplicationContext(),Fellow.devices(getApplicationContext()));main.post(()->{working=false;if(isDestroyed())return;render(d);schedule();});}catch(Exception ex){main.post(()->{starting=false;working=false;failed(ex);schedule();});}});
    }
    private void render(JSONObject d){
        if(device==null||!device.optString("id").equals(d.optString("id")))observedBrewStart=-1;
        if(d.optBoolean("isConnected")&&BrewPolicy.active(d.optBoolean("brewing"),d.optLong("brewStartTime"),d.optLong("brewEndTime")))observedBrewStart=d.optLong("brewStartTime");
        device=d;lastStatusAt=SystemClock.elapsedRealtime();if(recipe==null)return;updateCountdown();
        String name=d.optString("displayName","Aiden");Session.prefs(this).edit().putString("deviceName",name+" · "+d.optInt("ibWaterQuantity")+" ml").apply();BrewWidget.updateAll(this);
        recipe.setText(name+"\nInstant Brew · "+d.optInt("ibWaterQuantity")+" ml");JSONObject st=d.optJSONObject("state");String value=st==null?"":st.optString("value","");boolean error=st!=null&&!st.isNull("error")&&!"false".equals(st.optString("error"));
        String phase=BrewPolicy.sessionStage(d.optBoolean("isConnected"),d.optBoolean("brewing"),d.optLong("brewStartTime"),d.optLong("brewEndTime"),value,error,System.currentTimeMillis()/1000,observedBrewStart);
        stage.setText(phase);lights.removeAllViews();
        String highlighted=phase.startsWith("Pulse")||phase.equals("Pouring")?"Brewing":phase.equals("Paused")?"Attention":phase;
        for(String label:new String[]{"Ready","Bloom","Brewing","Drip finish","Complete","Attention"}){TextView lamp=new TextView(this);boolean on=label.equals(highlighted);lamp.setText((on?"●  ":"○  ")+label);lamp.setTextSize(15);lamp.setPadding(0,dp(3),0,dp(3));lamp.setTextColor(on?Color.rgb(41,100,57):Color.rgb(135,142,135));lamp.setContentDescription(label+(on?", active":", inactive"));lights.addView(lamp);}
        long last=Session.prefs(this).getLong("lastAttempt",0);boolean cooling=last>0&&System.currentTimeMillis()-last<60000;
        brew.setEnabled(d.optBoolean("isConnected")&&!BrewPolicy.active(d.optBoolean("brewing"),d.optLong("brewStartTime"),d.optLong("brewEndTime"))&&!cooling);
        if(phase.equals("Paused")||phase.equals("Attention"))status.setText("Check the brewer display for instructions.");
    }
    private void updateCountdown(){
        if(countdown==null)return;
        JSONObject d=device;
        if(d==null||!d.optBoolean("brewing")||!d.optBoolean("isConnected")){countdown.setVisibility(View.GONE);return;}
        long start=d.optLong("brewStartTime"),end=d.optLong("brewEndTime");
        if(end<=start||end<=0){countdown.setVisibility(View.GONE);return;}
        JSONObject state=d.optJSONObject("state");
        if(state!=null&&"pa".equals(state.optString("value"))){countdown.setText("Countdown paused");countdown.setVisibility(View.VISIBLE);return;}
        if(lastStatusAt==0||SystemClock.elapsedRealtime()-lastStatusAt>20000){countdown.setText("Waiting for timing update");countdown.setVisibility(View.VISIBLE);return;}
        long remaining=BrewPolicy.remainingSeconds(end,System.currentTimeMillis());
        if(remaining==0){countdown.setVisibility(View.GONE);return;}
        countdown.setText(String.format(java.util.Locale.getDefault(),"%d:%02d remaining",remaining/60,remaining%60));countdown.setVisibility(View.VISIBLE);
    }
    private void startBrew(){
        if(working)return;working=true;starting=true;pendingWidgetStart=false;main.removeCallbacks(poll);brew.setEnabled(false);status.setText("Sending one brew request...");
        IO.execute(()->{try{Fellow.brew(getApplicationContext());main.post(()->{starting=false;working=false;if(isDestroyed())return;status.setText("Request accepted. Watch the stage lights for the brewer’s response.");refresh();});}catch(Exception ex){main.post(()->{starting=false;working=false;failed(ex);schedule();});}});
    }
    private void chooseBrewer(){
        if(working)return;working=true;IO.execute(()->{try{JSONArray all=Fellow.devices(this);java.util.ArrayList<JSONObject> list=new java.util.ArrayList<>();java.util.ArrayList<String> names=new java.util.ArrayList<>();for(int i=0;i<all.length();i++){JSONObject d=all.getJSONObject(i);if(d.optString("id").matches("FB_[a-zA-Z0-9-]+")){list.add(d);names.add(d.optString("displayName","Aiden"));}}
            main.post(()->{working=false;if(isDestroyed())return;new AlertDialog.Builder(this).setTitle("Choose brewer").setItems(names.toArray(new String[0]),(dialog,index)->{JSONObject d=list.get(index);Session.prefs(this).edit().putString("device",d.optString("id")).apply();render(d);}).setNegativeButton("Cancel",null).show();schedule();});
        }catch(Exception ex){main.post(()->{working=false;failed(ex);schedule();});}});
    }
}