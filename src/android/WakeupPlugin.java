package org.nypr.cordova.wakeupplugin;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.SharedPreferences;
import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.res.Resources;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import to.laddr.darthema.R;

public class WakeupPlugin extends CordovaPlugin {

	protected static final String LOG_TAG = "WakeupPlugin";

	private static MediaPlayer mMediaPlayer = null;

	private static boolean isSystemSound = true;

	protected static final int ID_DAYLIST_OFFSET = 10010;
	protected static final int ID_ONETIME_OFFSET = 100000;
	protected static final int ID_SNOOZE_OFFSET = 10001;
	protected static final int ID_REPEAT_OFFSET = 10011;
	
	public static Map<String , Integer> daysOfWeek = new HashMap<String , Integer>() {
		private static final long serialVersionUID = 1L;
		{
			put("sunday", 0);
			put("monday", 1);
			put("tuesday", 2);
			put("wednesday", 3);
			put("thursday", 4);
			put("friday", 5);
			put("saturday", 6);
		}
	};

	public static CallbackContext connectionCallbackContext;

    @Override
    public void onReset() {
        // app startup
        Log.d(LOG_TAG, "Wakeup Plugin onReset");
        Bundle extras = cordova.getActivity().getIntent().getExtras();
		stopSound(cordova.getActivity().getApplicationContext());

        if (extras!=null && !extras.getBoolean("wakeup", false)) {
        	setAlarmsFromPrefs( cordova.getActivity().getApplicationContext() );
        }
        super.onReset();
    }

	@Override
	public void onResume(boolean multitasking) {
		Log.d(LOG_TAG, "Wakeup Plugin onResume");
		stopSound(cordova.getActivity().getApplicationContext());
		super.onResume(multitasking);
	}

	public static MediaPlayer playSound(Context context, Uri alert) {
		final AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
		final int volume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM);
		Log.d(LOG_TAG, "alarm volume..." + volume);

		if (volume != 0) {
			if (mMediaPlayer != null)
			{
				mMediaPlayer = null;
			}
			isSystemSound = true;
			mMediaPlayer = MediaPlayer.create(context, alert);
			mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
				public void onPrepared(MediaPlayer mp){
					mp.setVolume(volume, volume);
					mp.setLooping(false);
					mp.start();
				};
			});

			return mMediaPlayer;
		}
		else
		{
			return null;
		}
	}

	public static MediaPlayer playSound(Context context, int alert) {
		final AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
		final int volume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM);
		Log.d(LOG_TAG, "alarm volume..." + volume);

		if (volume != 0) {
			if (mMediaPlayer != null)
			{
				mMediaPlayer = null;
			}
			isSystemSound = false;
			mMediaPlayer = MediaPlayer.create(context, alert);
			mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
				public void onPrepared(MediaPlayer mp){
					mp.setVolume(volume, volume);
					mp.setLooping(false);
					mp.start();
				};
			});

			return mMediaPlayer;
		}
		else
		{
			return null;
		}
	}

	private void stopSound(Context context) {
		stopSound();
	}

	private void stopSound() {
		if (mMediaPlayer == null)
		{
			return;
		}

		if (mMediaPlayer.isPlaying())
		{
			if (mMediaPlayer.getCurrentPosition() < (mMediaPlayer.getDuration()-250))
			{
				if (Build.VERSION.SDK_INT<24 || !isSystemSound)
				{
					Log.d(LOG_TAG, "seeking to... " + (mMediaPlayer.getDuration()-250));
					mMediaPlayer.seekTo((mMediaPlayer.getDuration()-250));

					return;
				}
			}
		}

		stopAndReleaseSound();
	}

	public static void stopAndReleaseSound() {
		if (mMediaPlayer == null)
		{
			return;
		}

		mMediaPlayer.stop();
		mMediaPlayer.release();
		mMediaPlayer = null;
	}

	//Get an alarm sound. Try for an alarm. If none set, try notification, Otherwise, ringtone.
	public static Uri getAlarmUri() {
		Uri alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);

		if (alert == null) {
			alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
		};

		if (alert == null) {
			alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
		}

		return alert;
	};

	public static int getAlarmUri(Context context, String alert) {
		int alertId = 0;
		if (!alert.isEmpty())
		{
			alertId = context.getResources().getIdentifier(alert,"raw", context.getPackageName());
		}

		return alertId;
	};

	@Override
	public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
		boolean ret=true;
		try {
			if(action.equalsIgnoreCase("wakeup")) {
				Log.d(LOG_TAG, "scheduling wakeup...");
				JSONObject options=args.getJSONObject(0);
				JSONArray alarms;
				if (options.has("alarms")==true) {
					alarms = options.getJSONArray("alarms");
				} else {
					alarms = new JSONArray(); // default to empty array
				}

				saveToPrefs(cordova.getActivity().getApplicationContext(), alarms);
				setAlarms(cordova.getActivity().getApplicationContext(), alarms, true);

				WakeupPlugin.connectionCallbackContext = callbackContext;
				PluginResult pluginResult = new PluginResult(PluginResult.Status.OK);
				pluginResult.setKeepCallback(true);
				callbackContext.sendPluginResult(pluginResult);
			}else if(action.equalsIgnoreCase("snooze")) {
				JSONObject options=args.getJSONObject(0);
				if (options.has("alarms")==true) {
					Log.d(LOG_TAG, "scheduling snooze...");
					JSONArray alarms = options.getJSONArray("alarms");
					setAlarms(cordova.getActivity().getApplicationContext(), alarms, false);
				}

				WakeupPlugin.connectionCallbackContext = callbackContext;
				PluginResult pluginResult = new PluginResult(PluginResult.Status.OK);
				pluginResult.setKeepCallback(true);
				callbackContext.sendPluginResult(pluginResult);
			}else if(action.equalsIgnoreCase("cancelAlarm")) {
				cancelAlarms(cordova.getActivity().getApplicationContext(), args);

				WakeupPlugin.connectionCallbackContext = callbackContext;
				PluginResult pluginResult = new PluginResult(PluginResult.Status.OK);
				pluginResult.setKeepCallback(true);
				callbackContext.sendPluginResult(pluginResult);
			}else if(action.equalsIgnoreCase("playSound")) {
				//Uri audioUri = Uri.parse(args.getString(0));

				playSound(cordova.getActivity().getApplicationContext(), getAlarmUri());

				WakeupPlugin.connectionCallbackContext = callbackContext;
				PluginResult pluginResult = new PluginResult(PluginResult.Status.OK);
				pluginResult.setKeepCallback(true);
				callbackContext.sendPluginResult(pluginResult);
			}else if(action.equalsIgnoreCase("stopSound")) {
				stopSound(cordova.getActivity().getApplicationContext());

				WakeupPlugin.connectionCallbackContext = callbackContext;
				PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, getLaunchDetails("stopSound"));
				pluginResult.setKeepCallback(true);
				callbackContext.sendPluginResult(pluginResult);
			}else if(action.equalsIgnoreCase("getLaunchDetails")) {
				WakeupPlugin.connectionCallbackContext = callbackContext;
				PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, getLaunchDetails("launchDetails"));
				pluginResult.setKeepCallback(true);
				callbackContext.sendPluginResult(pluginResult);
			}
			else{
				PluginResult pluginResult = new PluginResult(PluginResult.Status.ERROR, LOG_TAG + " error: invalid action (" + action + ")");
				pluginResult.setKeepCallback(true);
				callbackContext.sendPluginResult(pluginResult);
				ret=false;
			}
		} catch (JSONException e) {
			PluginResult pluginResult = new PluginResult(PluginResult.Status.ERROR, LOG_TAG + " error: invalid json");
			pluginResult.setKeepCallback(true);
			callbackContext.sendPluginResult(pluginResult);
			ret = false;
		} catch (Exception e) {
			PluginResult pluginResult = new PluginResult(PluginResult.Status.ERROR, LOG_TAG + " error: " + e.getMessage());
			pluginResult.setKeepCallback(true);
			callbackContext.sendPluginResult(pluginResult);
			ret = false;
		}
		return ret;
	}

	private JSONObject getLaunchDetails(String type){
		JSONObject o = new JSONObject();
	try {
		Bundle extrasBundle = cordova.getActivity().getIntent().getExtras();

		String extras = null;
		if (extrasBundle!=null && extrasBundle.get("extra")!=null) {
			extras = extrasBundle.get("extra").toString();
		}

		type = type.isEmpty() ? "launchDetails" : type;

		o.put("type", type);
		if (extras!=null) {
			o.put("extra", extras);
		}
		o.put("cdvStartInBackground", true);
	} catch (JSONException e) {
		e.printStackTrace();
	}
		return o;
	}


  public static void setAlarmsFromPrefs(Context context) {
    try {
      SharedPreferences prefs;
      prefs = PreferenceManager.getDefaultSharedPreferences(context);
      String a = prefs.getString("alarms", "[]");
      Log.d(LOG_TAG, "setting alarms:\n" + a);
      JSONArray alarms = new JSONArray( a );
      WakeupPlugin.setAlarms(context, alarms, true);
    } catch (JSONException e) {
      e.printStackTrace();
    }
  }

	@SuppressLint({ "SimpleDateFormat", "NewApi" })
	protected static void setAlarms(Context context, JSONArray alarms, boolean cancelAlarms) throws JSONException{
		if (cancelAlarms)
		{
			cancelAll(context);
		}

		for(int i=0;i<alarms.length();i++){
			JSONObject alarm=alarms.getJSONObject(i);

			if (alarm.has("id") && cancelAlarms)
			{
				cancelAlarm(context, alarm.getInt("id"));
			}

			String type = "onetime";
			if (alarm.has("type")){
				type = alarm.getString("type");
			}

			if (!alarm.has("time")){
				throw new JSONException("alarm missing time: " + alarm.toString());
			}

			if ( type.equals("onetime")) {
				Calendar alarmDate = null;
				Object time=alarm.get("time");

				if (time instanceof JSONObject)
				{
					alarmDate=getOneTimeAlarmDate(alarm.getJSONObject("time"));
				}
				else if (time instanceof Long)
				{
					alarmDate=getOneTimeAlarmDate(alarm.getLong("time"));
				}

				Intent intent = new Intent(context, WakeupReceiver.class);

				if(alarm.has("sound")){
					intent.putExtra("sound", alarm.getString("sound"));
				}

				if(alarm.has("extra")){
					intent.putExtra("extra", alarm.getJSONObject("extra").toString());
					intent.putExtra("type", type);
					if (alarmDate != null)
					{
						intent.putExtra("time", alarmDate.getTimeInMillis());
					}
					intent.putExtra("skipOnAwake", alarm.optBoolean("skipOnAwake", false));
					intent.putExtra("skipOnRunning", alarm.optBoolean("skipOnRunning", false));
					intent.putExtra("playSoundOnly", alarm.optBoolean("playSoundOnly", false));
					intent.putExtra("playSound", alarm.optBoolean("playSound", false));
					intent.putExtra("startInBackground", alarm.optBoolean("startInBackground", true));
				}

				if (!alarm.has("id"))
				{
					setNotification(context, type, alarmDate, intent, ID_ONETIME_OFFSET);
				}
				else
				{
					setNotification(context, type, alarmDate, intent, alarm.getInt("id"));
				}
			} else if ( type.equals("daylist") ) {
				JSONArray days=alarm.getJSONArray("days");
				JSONObject time=alarm.getJSONObject("time");
				for (int j=0;j<days.length();j++){
					Calendar alarmDate=getAlarmDate(time, daysOfWeek.get(days.getString(j)));
					Intent intent = new Intent(context, WakeupReceiver.class);
					if(alarm.has("extra")){
						intent.putExtra("extra", alarm.getJSONObject("extra").toString());
						intent.putExtra("type", type);
						intent.putExtra("time", time.toString());
						intent.putExtra("day", days.getString(j));
						intent.putExtra("skipOnAwake", alarm.optBoolean("skipOnAwake", false));
						intent.putExtra("skipOnRunning", alarm.optBoolean("skipOnRunning", false));
						intent.putExtra("playSoundOnly", alarm.optBoolean("playSoundOnly", false));
						intent.putExtra("playSound", alarm.optBoolean("playSound", false));
						intent.putExtra("startInBackground", alarm.optBoolean("startInBackground", true));
					}

					setNotification(context, type, alarmDate, intent, ID_DAYLIST_OFFSET + daysOfWeek.get(days.getString(j)));
				}
			} else if ( type.equals("snooze") ) {
				cancelAlarm(context, ID_SNOOZE_OFFSET);
				JSONObject time=alarm.getJSONObject("time");
				Calendar alarmDate=getTimeFromNow(time);
				Intent intent = new Intent(context, WakeupReceiver.class);
				if(alarm.has("extra")){
					intent.putExtra("extra", alarm.getJSONObject("extra").toString());
					intent.putExtra("type", type);
					intent.putExtra("skipOnAwake", alarm.optBoolean("skipOnAwake", false));
					intent.putExtra("skipOnRunning", alarm.optBoolean("skipOnRunning", false));
					intent.putExtra("playSoundOnly", alarm.optBoolean("playSoundOnly", false));
					intent.putExtra("playSound", alarm.optBoolean("playSound", false));
					intent.putExtra("startInBackground", alarm.optBoolean("startInBackground", true));
				}
				setNotification(context, type, alarmDate, intent, ID_SNOOZE_OFFSET);
			} else if ( type.equals("repeating")) {
				JSONObject time=alarm.getJSONObject("time");
				Calendar alarmDate = getRepeatingAlertDate(time);
				Intent intent = new Intent(context, WakeupReceiver.class);
				if(alarm.has("extra")){
					intent.putExtra("extra", alarm.getJSONObject("extra").toString());
					intent.putExtra("type", type);
					intent.putExtra("skipOnAwake", alarm.optBoolean("skipOnAwake", false));
					intent.putExtra("skipOnRunning", alarm.optBoolean("skipOnRunning", false));
					intent.putExtra("playSoundOnly", alarm.optBoolean("playSoundOnly", false));
					intent.putExtra("playSound", alarm.optBoolean("playSound", false));
					intent.putExtra("startInBackground", alarm.optBoolean("startInBackground", true));
				}

				setNotification(context, type, alarmDate, intent, ID_REPEAT_OFFSET);
			}
		}
	}

	protected static void setNotification(Context context, String type, Calendar alarmDate, Intent intent, int id) throws JSONException{
		if(alarmDate!=null){
			intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
			PendingIntent sender = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT);
			AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

			if (type.equals("repeating")) {
				Log.d(LOG_TAG, "setting alarm every " + alarmDate.get(Calendar.MINUTE) + " minutes; id " + id);

				TimeZone defaultTimeZone = TimeZone.getDefault();
				Calendar now = new GregorianCalendar(defaultTimeZone);
				now.set(Calendar.MINUTE, now.get(Calendar.MINUTE) + alarmDate.get(Calendar.MINUTE));

				long intervalMillis = TimeUnit.MINUTES.toMillis(alarmDate.get(Calendar.MINUTE));
				alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, now.getTimeInMillis(), intervalMillis, sender);
			} else {
				SimpleDateFormat sdf=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
				Log.d(LOG_TAG,"setting alarm at " + sdf.format(alarmDate.getTime()) + "; id " + id);

				if (Build.VERSION.SDK_INT>=19) {
					alarmManager.setExact(AlarmManager.RTC_WAKEUP, alarmDate.getTimeInMillis(), sender);
				} else {
					alarmManager.set(AlarmManager.RTC_WAKEUP, alarmDate.getTimeInMillis(), sender);
				}
			}

			if(WakeupPlugin.connectionCallbackContext!=null) {
				JSONObject o=new JSONObject();
				o.put("type", "set");
				o.put("alarm_type", type);
				o.put("alarm_date", alarmDate.getTimeInMillis());
				
				Log.d(LOG_TAG, "alarm time in millis: " + alarmDate.getTimeInMillis());
				
				PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, o);
				pluginResult.setKeepCallback(true);
				WakeupPlugin.connectionCallbackContext.sendPluginResult(pluginResult);  
			}
		}
	}

	protected static void cancelAlarm(Context context, int id){
		Log.d(LOG_TAG, "cancelling alarm id " + id);
		Intent intent = new Intent(context, WakeupReceiver.class);
		PendingIntent sender = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT);
		AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		alarmManager.cancel(sender);
	}
	
	protected static void cancelAll(Context context){
		Log.d(LOG_TAG, "canceling all alarms");
		cancelAlarm(context, ID_ONETIME_OFFSET);
		cancelAlarm(context, ID_SNOOZE_OFFSET);
		for (int i=0;i<7;i++)
		{
			cancelAlarm(context, ID_DAYLIST_OFFSET + i);
		}
	}
	
	protected static void cancelAlarms(Context context, JSONArray ids){
		Log.d(LOG_TAG, "canceling alarms by JS call");
		try {
			for (int i=0;i<ids.length();i++){
				cancelAlarm(context, ids.getInt(i));
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	protected static Calendar getRepeatingAlertDate(JSONObject time) throws JSONException {
		TimeZone defaultTimeZone = TimeZone.getDefault();
		Calendar calendar = new GregorianCalendar(defaultTimeZone);

		if (time.has("minutes")) {
			calendar.set(Calendar.MINUTE, time.getInt("minutes"));
		} else {
			calendar = null;
		}

		return calendar;
	}

	protected static Calendar getOneTimeAlarmDate( JSONObject time) throws JSONException {
		TimeZone defaultz = TimeZone.getDefault();
		Calendar calendar = new GregorianCalendar(defaultz);
		Calendar now = new GregorianCalendar(defaultz);
		now.setTime(new Date());
		calendar.setTime(new Date());

		int hour=(time.has("hour")) ? time.getInt("hour") : -1;
		int minute=(time.has("minute")) ? time.getInt("minute") : 0;

		if(hour>=0){
			calendar.set(Calendar.HOUR_OF_DAY, hour);
			calendar.set(Calendar.MINUTE, minute);
			calendar.set(Calendar.SECOND, 0);
			calendar.set(Calendar.MILLISECOND,0);

			if (calendar.before(now)){
				calendar.set(Calendar.DATE, calendar.get(Calendar.DATE) + 1);
			}
		}else{
			calendar=null;
		}

		return calendar;
	}

	protected static Calendar getOneTimeAlarmDate( long time) throws JSONException {
		TimeZone defaultz = TimeZone.getDefault();
		Calendar calendar = new GregorianCalendar(defaultz);
		calendar.setTimeInMillis(time);
		Calendar now = new GregorianCalendar(defaultz);
		now.setTime(new Date());

		if (calendar.before(now)){
			calendar=null;
		}

		return calendar;
	}
	
	protected static Calendar getAlarmDate( JSONObject time, int dayOfWeek) throws JSONException {
		TimeZone defaultz = TimeZone.getDefault();
		Calendar calendar = new GregorianCalendar(defaultz);
		Calendar now = new GregorianCalendar(defaultz);
		now.setTime(new Date());
		calendar.setTime(new Date());

		int hour=(time.has("hour")) ? time.getInt("hour") : -1;
		int minute=(time.has("minute")) ? time.getInt("minute") : 0;

		if(hour>=0){
			calendar.set(Calendar.HOUR_OF_DAY, hour);
			calendar.set(Calendar.MINUTE, minute);
			calendar.set(Calendar.SECOND, 0);
			calendar.set(Calendar.MILLISECOND,0);

			int currentDayOfWeek=calendar.get(Calendar.DAY_OF_WEEK); // 1-7 = Sunday-Saturday
			currentDayOfWeek--; // make zero-based

			// add number of days until 'dayOfWeek' occurs
			int daysUntilAlarm=0;
			if(currentDayOfWeek>dayOfWeek){
				// currentDayOfWeek=thursday (4); alarm=monday (1) -- add 4 days
				daysUntilAlarm=(6-currentDayOfWeek) + dayOfWeek + 1; // (days until the end of week) + dayOfWeek + 1
			}else if(currentDayOfWeek<dayOfWeek){
				// example: currentDayOfWeek=monday (1); dayOfWeek=thursday (4) -- add three days
				daysUntilAlarm=dayOfWeek-currentDayOfWeek;
			}else{
				if(now.after(calendar.getTime())){
					daysUntilAlarm=7;
				}else{
					daysUntilAlarm=0;
				}
			}

			calendar.set(Calendar.DATE, now.get(Calendar.DATE) + daysUntilAlarm);
		}else{
			calendar=null;
		}

		return calendar;
	}

	protected static Calendar getTimeFromNow( JSONObject time) throws JSONException {
		TimeZone defaultz = TimeZone.getDefault();
		Calendar calendar = new GregorianCalendar(defaultz);
		calendar.setTime(new Date());

		int seconds=(time.has("seconds")) ? time.getInt("seconds") : -1;
		
		if(seconds>=0){
			calendar.add(Calendar.SECOND, seconds);
		}else{
			calendar=null;
		}

		return calendar;
	}
	
	protected static void saveToPrefs(Context context, JSONArray alarms) {
		SharedPreferences prefs;
		SharedPreferences.Editor editor;
	
		prefs = PreferenceManager.getDefaultSharedPreferences(context);
		editor = prefs.edit();
		editor.putString("alarms", alarms.toString());
		editor.commit();

	}

}
