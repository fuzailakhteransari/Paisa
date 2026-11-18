package in.paisa.personal;
import android.app.*;
import android.os.*;
import android.service.notification.*;
import org.json.*;
import java.util.concurrent.*;

public class CaptureService extends NotificationListenerService {
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 @Override public void onNotificationPosted(StatusBarNotification sbn){
  if(sbn==null||sbn.getPackageName().equals(getPackageName())||(sbn.getNotification().flags&Notification.FLAG_GROUP_SUMMARY)!=0)return;
  worker.execute(()->{try(Store store=new Store(this)){JSONObject cfg=store.settings();if(!cfg.optBoolean("captureNotifications"))return;String pkg=sbn.getPackageName();boolean email=contains(cfg.optJSONArray("emailApps"),pkg),bank=contains(cfg.optJSONArray("bankApps"),pkg);if(!email&&!bank)return;Bundle b=sbn.getNotification().extras;String title=String.valueOf(b.getCharSequence(Notification.EXTRA_TITLE,"")),big=String.valueOf(b.getCharSequence(Notification.EXTRA_BIG_TEXT,"")),small=String.valueOf(b.getCharSequence(Notification.EXTRA_TEXT,""));String text=title+" "+(big.isEmpty()?small:big);if(text.length()<10)return;long date=sbn.getNotification().when>0?sbn.getNotification().when:sbn.getPostTime();if(Math.abs(System.currentTimeMillis()-date)>7L*86400000)date=sbn.getPostTime();if(Capture.ingest(this,pkg,text,date,email?"Email":"Bank notification",true))Reminders.check(this);}catch(Exception ignored){}});
 }
 private boolean contains(JSONArray a,String s){if(a==null)return false;for(int i=0;i<a.length();i++)if(s.equals(a.optString(i)))return true;return false;}
 @Override public void onDestroy(){worker.shutdown();super.onDestroy();}
}
