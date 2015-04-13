package com.contextnotifier;


import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DecimalFormat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

public class Monitor extends Service {

    SharedPreferences prefs;
	NotificationManager NM;
	Handler repeater;
    public enum Trend {UPWARD, DOWNWARD, SAME}
    Trend trend;
    double high = 0.0;
    double low = 0.0;
    double average = 0.0;
    long updated = 0;
	int interval = 300000;

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		//TODO do something useful
		return Service.START_STICKY;
	}

	@Override
	public void onCreate() {
		NM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
		prefs = getSharedPreferences("ContextNotifier", 0);
        high = Double.parseDouble(prefs.getString("high", "0"));
        low = Double.parseDouble(prefs.getString("low", "0"));
        average = Double.parseDouble(prefs.getString("avg", "0"));
        updated = prefs.getLong("updated", 0);
		interval = prefs.getInt("interval", 300000);
		repeater = new Handler();
		coreThread.run();
		Log.v("BTC", "start");
	}

    @Override
    public void onDestroy() {
    }

    public void saveData(){
        SharedPreferences.Editor editor =  prefs.edit();
        editor.putString("high", Double.toString(high));
        editor.putString("low", Double.toString(low));
        editor.putString("avg", Double.toString(average));
        editor.putLong("updated", updated);
        editor.commit();
    }
	
	public Runnable coreThread = new Runnable(){
        @Override
        public void run() {
            new BTC().execute();
        	repeater.postDelayed(coreThread, interval);
        }
	};


	private class BTC extends AsyncTask<Void, Void, String> {

		@Override
		protected String doInBackground(Void... params) {
			try{
				URL url = new URL("https://btc-e.com/api/2/btc_usd/ticker");
				HttpURLConnection conn = (HttpURLConnection) url.openConnection();
				conn.setConnectTimeout(5000);
				conn.setReadTimeout(10000);
				BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
				String str = in.readLine();
				Log.v("BTC", str);
				return str;
			}catch(Exception e){
				e.printStackTrace();
				return "";
			}
		}      

		@Override
		protected void onPostExecute(String result) {
            String notification_string = "";
			JSONObject ticker = parseCurrentJSON(result);
            double newhigh = Double.parseDouble(new DecimalFormat("######.##").format(ticker.optDouble("high")));
            double newlow = Double.parseDouble(new DecimalFormat("######.##").format(ticker.optDouble("low")));
            double newaverage = Double.parseDouble(new DecimalFormat("######.##").format(ticker.optDouble("avg")));
            double last = Double.parseDouble(new DecimalFormat("######.##").format(ticker.optDouble("last")));
            long newupdated = ticker.optLong("updated");
            if (newlow >= high){
                trend = Trend.UPWARD;
            }else if (newhigh <= low){
                trend = Trend.DOWNWARD;
            }else{
                trend = Trend.SAME;
            }
            if (trend == Trend.UPWARD || trend == Trend.DOWNWARD || high == 0.0 || low == 0.0){
                notification_string = "Trend is " + trend.toString() + "\n\nHigh/Low: " + newhigh + "/" + newlow + "\nUpdated " + (System.currentTimeMillis()/1000 - newupdated) + " sec ago\n\nHigh/Low: " + high + "/" + low + "\nUpdated " + (System.currentTimeMillis()/1000 - updated) + " sec ago\n\nLast Price: " + last;
                Log.v("BTC", notification_string);
                high = newhigh;
                low = newlow;
                average = newaverage;
                updated = newupdated;
                saveData();
            }else{
                notification_string = "High/Low: " + newhigh + "/" + newlow + "\nUpdated " + (System.currentTimeMillis()/1000 - newupdated) + " sec ago\n\nLast Price: " + last;
            }
            showNotification(notification_string);
		}

		@Override
		protected void onPreExecute() {
		}

		@Override
		protected void onProgressUpdate(Void... values) {
		}
	}   
	
	public JSONObject parseCurrentJSON(String str){
		try {
			JSONObject json = new JSONObject(str);
			JSONObject ticker = json.optJSONObject("ticker");
            return ticker;
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return new JSONObject();
	}
	
	public void showNotification(String msg){
		int icon = R.drawable.bitcoin;
        Resources r = this.getResources();
        float density = r.getDisplayMetrics().density;
        Bitmap bm = BitmapFactory.decodeResource(r, icon);
        Bitmap b = Bitmap.createScaledBitmap(bm, (int) (25 * density + .05), (int) (25 * density + .05), false);
		Notification.Builder mBuilder =
			        new Notification.Builder(this)
			        .setContentTitle("BSD/USD")
			        .setContentText("Trend is " + trend.toString())
                    .setSmallIcon(icon)
                    .setLargeIcon(b)
                    .setStyle(new Notification.BigTextStyle()
                            .bigText(msg));
        if (trend != Trend.SAME){
			    Uri uri= RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
			    mBuilder.setSound(uri);
        }
	    NM.notify(855, mBuilder.build());
	}

	@Override
	public IBinder onBind(Intent intent) {
		//TODO for communication return IBinder implementation
		return null;
	}
} 
