package in.paisa.personal;

import android.app.job.*;
import android.content.*;
import android.os.*;

public final class CloudJobs extends JobService {
 private static final int ONCE=7401,PERIODIC=7402;
 private static long last;
 public static final ThreadLocal<Boolean> APPLYING=new ThreadLocal<>();
 public static boolean active(Context context){return context.getSharedPreferences("cloud-flags",Context.MODE_PRIVATE).getBoolean("scheduled",false);}
 public static synchronized void changed(Context context){if(Boolean.TRUE.equals(APPLYING.get())||!active(context))return;context.getSharedPreferences("cloud-flags",Context.MODE_PRIVATE).edit().putBoolean("dirty",true).apply();long now=SystemClock.elapsedRealtime();if(now-last<1500)return;last=now;new Handler(Looper.getMainLooper()).postDelayed(()->schedule(context),1800);}
 public static void schedule(Context context){if(!active(context))return;JobScheduler jobs=context.getSystemService(JobScheduler.class);ComponentName component=new ComponentName(context,CloudJobs.class);jobs.schedule(new JobInfo.Builder(ONCE,component).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setMinimumLatency(2000).setBackoffCriteria(30000,JobInfo.BACKOFF_POLICY_EXPONENTIAL).setPersisted(true).build());if(jobs.getPendingJob(PERIODIC)==null)jobs.schedule(new JobInfo.Builder(PERIODIC,component).setPeriodic(15*60000L).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true).build());}
 public static void cancel(Context context){JobScheduler jobs=context.getSystemService(JobScheduler.class);jobs.cancel(ONCE);jobs.cancel(PERIODIC);}
 @Override public boolean onStartJob(JobParameters parameters){CloudSync.execute(()->{boolean retry=CloudSync.run(this,"");jobFinished(parameters,retry&&active(this));});return true;}
 @Override public boolean onStopJob(JobParameters parameters){return active(this);}
}
