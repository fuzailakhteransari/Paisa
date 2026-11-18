package in.paisa.personal;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import org.json.*;
import java.util.*;

public class SmsReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        if(!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;
        SmsMessage[] parts=Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if(parts.length==0)return;
        StringBuilder body=new StringBuilder();for(SmsMessage p:parts)body.append(p.getMessageBody());
        final PendingResult result=goAsync();String sender=parts[0].getOriginatingAddress();long date=parts[0].getTimestampMillis();new Thread(()->{try{if(ingest(context,sender,body.toString(),date,true))Reminders.check(context);}finally{result.finish();}},"paisa-sms").start();
    }
    public static boolean ingest(Context c,String sender,String body,long date,boolean notify) {
        return Capture.ingest(c,sender,body,date,"SMS",notify);
    }
    public static void sendNotification(Context c,JSONObject row) {
        if(Build.VERSION.SDK_INT>=33&&c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
        NotificationManager nm=c.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("review","Payment categories",NotificationManager.IMPORTANCE_DEFAULT));
        Intent open=new Intent(c,MainActivity.class).putExtra("entryId",row.optString("id")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int id=row.optString("id").hashCode();
        PendingIntent pi=PendingIntent.getActivity(c,id,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        String amount=String.format(Locale.forLanguageTag("en-IN"),"₹%,.2f",row.optLong("amount")/100.0);
        String message=row.optString("kind").equals("income")?"What was this money received for?":"What was this payment for?";
        Notification n=new Notification.Builder(c,"review").setSmallIcon(in.paisa.personal.R.drawable.ic_paisa).setContentTitle(amount+" · "+row.optString("title")).setContentText(message+" Tap to review.").setContentIntent(pi).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE).addAction(new Notification.Action.Builder(null,"Choose category",pi).build()).build();
        nm.notify(id,n);
    }
}
