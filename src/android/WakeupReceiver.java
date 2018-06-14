package org.nypr.cordova.wakeupplugin;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.apache.cordova.PluginResult;
import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;

public class WakeupReceiver extends BroadcastReceiver {

	private static final String LOG_TAG = "WakeupReceiver";

	public boolean isRunning(Context ctx) {
		ActivityManager activityManager = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
		List<ActivityManager.RunningAppProcessInfo> appProcesses = activityManager.getRunningAppProcesses();
		if (appProcesses == null) {
			return false;
		}
		final String packageName = ctx.getPackageName();
		for (ActivityManager.RunningAppProcessInfo appProcess : appProcesses) {
			if (appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND && appProcess.processName.equals(packageName)) {
				return true;
			}
		}
		return false;
	}

	public void performAlarm(Context context, Intent intent)
	{
		SimpleDateFormat sdf=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

		try {
			Bundle extrasBundle = intent.getExtras();
			String extras=null;
			if (extrasBundle!=null && extrasBundle.get("extra")!=null) {
				extras = extrasBundle.get("extra").toString();
			}

			String packageName = context.getPackageName();
			Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(packageName);
			launchIntent.putExtra("cdvStartInBackground", true);
			if (extras!=null) {
				launchIntent.putExtra("extra", extras);
			}

			String className = launchIntent.getComponent().getClassName();
			Log.d(LOG_TAG, "launching activity for class " + className);

			@SuppressWarnings("rawtypes")
			Class c = Class.forName(className);

			Intent i = new Intent(context, c);
			i.putExtra("wakeup", true);

			if (extrasBundle != null && extrasBundle.get("startInBackground") != null) {
				if (extrasBundle.get("startInBackground").equals(true)) {
					Log.d(LOG_TAG, "starting app in background");
					i.putExtra("cdvStartInBackground", true);
				}
			}

			if (extras!=null) {
				i.putExtra("extra", extras);
			}
			i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			context.startActivity(i);

			if(WakeupPlugin.connectionCallbackContext!=null) {
				JSONObject o=new JSONObject();
				o.put("type", "wakeup");
				if (extras!=null) {
					o.put("extra", extras);
				}
				o.put("cdvStartInBackground", true);
				PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, o);
				pluginResult.setKeepCallback(true);
				WakeupPlugin.connectionCallbackContext.sendPluginResult(pluginResult);
			}

			if (extrasBundle!=null && extrasBundle.getString("type")!=null && extrasBundle.getString("type").equals("daylist")) {
				// repeat in one week
				Date next = new Date(new Date().getTime() + (7 * 24 * 60 * 60 * 1000));
				Log.d(LOG_TAG,"resetting alarm at " + sdf.format(next));

				Intent reschedule = new Intent(context, WakeupReceiver.class);
				if (extras!=null) {
					reschedule.putExtra("extra", extras);
				}
				reschedule.putExtra("day", WakeupPlugin.daysOfWeek.get(extrasBundle.get("day")));
				reschedule.putExtra("cdvStartInBackground", true);

				PendingIntent sender = PendingIntent.getBroadcast(context, 19999 + WakeupPlugin.daysOfWeek.get(extrasBundle.get("day")), intent, PendingIntent.FLAG_UPDATE_CURRENT);
				AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

				//Code added for doze or app stand by mode
				if (Build.VERSION.SDK_INT>=19) 
				{
					if (Build.VERSION.SDK_INT>=23) 
					{
						alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.getTime(), sender);
					} else 
					{
						//Below code commented as it was not working for API22
						//alarmManager.setExact(AlarmManager.RTC_WAKEUP, next.getTime(), sender);
						
						//Code is working for API22
						AlarmManager.AlarmClockInfo info = new AlarmManager.AlarmClockInfo(next.getTime(), null);
						alarmManager.setAlarmClock(info, sender);
					}
				} else 
				{
					alarmManager.set(AlarmManager.RTC_WAKEUP, next.getTime(), sender);
				}
			}
		} catch (JSONException e){
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
	}

	@SuppressLint({ "SimpleDateFormat", "NewApi" })
	@Override
	public void onReceive(final Context context, final Intent intent) {
		SimpleDateFormat sdf=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		Long currentTime = new Date().getTime();
		Log.d(LOG_TAG, "wakeuptimer expired at " + sdf.format(currentTime));

		Bundle extrasBundle = intent.getExtras();

		//Following block will not fire the alarms in past for oneTime, if user change the date/time to future and differece is >= 2 min.
		if (extrasBundle!=null && extrasBundle.getString("type")!=null && extrasBundle.getString("type").equals("onetime")) {
			Long time = extrasBundle.getLong("time");
			if (time != null)
			{
				if ((currentTime - time) >= (2*60000))
				{
					return;
				}
			}
		}

		if (extrasBundle != null && extrasBundle.get("skipOnAwake") != null) {
			if (extrasBundle.get("skipOnAwake").equals(true)) {
				PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
				boolean isScreenAwake = (Build.VERSION.SDK_INT < 20 ? powerManager.isScreenOn() : powerManager.isInteractive());

				if (isScreenAwake) {
					Log.d(LOG_TAG, "screen is awake. Postponing launch.");
					return;
				}
			}
		}

		if (extrasBundle != null && extrasBundle.get("skipOnRunning") != null) {
			if (extrasBundle.get("skipOnRunning").equals(true)) {
				if (isRunning(context)) {
					Log.d(LOG_TAG, "app is already running. No need to launch");
					return;
				}
			}
		}

		if (extrasBundle != null && extrasBundle.get("playSoundOnly") != null) {
			if (extrasBundle.get("playSoundOnly").equals(true)) {
				Log.d(LOG_TAG, "playing sound only.");
				MediaPlayer mMediaPlayer = WakeupPlugin.playSound(context, WakeupPlugin.getAlarmUri());
				if (mMediaPlayer != null)
				{
					mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
						public void onCompletion(MediaPlayer mp){
							WakeupPlugin.stopAndReleaseSound();
						};
					});

					mMediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
						public boolean onError(MediaPlayer mp, int var2, int var3){
							WakeupPlugin.stopAndReleaseSound();
							return false;
						};
					});
				}
				return;
			}
		}

		if (extrasBundle != null && extrasBundle.get("playSound") != null) {
			if (extrasBundle.get("playSound").equals(true)) {
				Log.d(LOG_TAG, "playing sound.");

				MediaPlayer mMediaPlayer = null;

				String sound = extrasBundle.getString("sound", "");
				Log.d(LOG_TAG, "sound " + sound);
				if (!sound.isEmpty())
				{
					int soundId = WakeupPlugin.getAlarmUri(context, sound);
					Log.d(LOG_TAG, "soundId " + soundId);
					if (soundId != 0)
					{
						mMediaPlayer = WakeupPlugin.playSound(context, soundId);
					}
				}

				if (mMediaPlayer == null)
				{
					mMediaPlayer = WakeupPlugin.playSound(context, WakeupPlugin.getAlarmUri());
				}

				if (mMediaPlayer != null)
				{
					Log.d(LOG_TAG, "in media player condition");
					Log.d(LOG_TAG, "sound duration... " + mMediaPlayer.getDuration());
					mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
						public void onCompletion(MediaPlayer mp){
							Log.d(LOG_TAG, "in on complete");
							WakeupPlugin.stopAndReleaseSound();
							performAlarm(context, intent);
						};
					});

					mMediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
						public boolean onError(MediaPlayer mp, int var2, int var3){
							WakeupPlugin.stopAndReleaseSound();
							performAlarm(context, intent);
							return false;
						};
					});

					return;
				}
			}
		}

		performAlarm(context, intent);
	}
}
