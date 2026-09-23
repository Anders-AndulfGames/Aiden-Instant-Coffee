package com.andulf.aiden;

import android.app.PendingIntent;
import android.appwidget.*;
import android.content.*;
import android.widget.RemoteViews;

public class BrewWidget extends AppWidgetProvider {
    static void updateAll(Context c){int[] ids=AppWidgetManager.getInstance(c).getAppWidgetIds(new ComponentName(c,BrewWidget.class));new BrewWidget().onUpdate(c,AppWidgetManager.getInstance(c),ids);}
    @Override public void onUpdate(Context c,AppWidgetManager manager,int[] ids){
        for(int id:ids){
            RemoteViews view=new RemoteViews(c.getPackageName(),R.layout.widget);
            view.setImageViewResource(R.id.widget_brew,R.drawable.icon);
            Intent intent=new Intent(c,WidgetActivity.class).setAction("com.andulf.aiden.WIDGET_BREW").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            view.setOnClickPendingIntent(R.id.widget_brew,PendingIntent.getActivity(c,id,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
            manager.updateAppWidget(id,view);
        }
    }
}