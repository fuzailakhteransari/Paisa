package in.paisa.personal;
import android.content.*;
import org.json.*;
import java.util.*;

public final class Capture {
 public static boolean ingest(Context context,String sender,String body,long date,String source,boolean notify){
  if(body==null||body.length()>200000)return false;
  if(source.equals("SMS")&&(sender==null||!sender.matches("(?i)[a-z0-9-]{3,24}")||!sender.matches(".*[a-zA-Z].*")))return false;
  if(source.equals("Email")&&!EmailText.hasFinancialContext(body))return false;
  String hint=SmsParser.bankName(sender==null?"":sender).replaceAll("[^a-zA-Z0-9]","");
  SmsParser.Parsed p=SmsParser.parse(source.equals("SMS")?sender:hint.equals("Bank")?"BANKALERT":hint,source.equals("SMS")?body:body+" bank",date);
  if(p==null){try{JSONObject bill=NoticeParser.bill(body,sender,date);if(bill==null)return false;try(Store store=new Store(context)){if(store.get("bills",bill.getString("id"))==null){if(store.get("accounts",bill.optString("accountId"))==null)store.put("accounts",new JSONObject().put("id",bill.optString("accountId")).put("name",bill.optString("name")).put("type","credit").put("opening",0).put("openingAt",0));store.put("bills",bill.put("source",source));context.sendBroadcast(new Intent("in.paisa.DATA_CHANGED").setPackage(context.getPackageName()));return true;}}}catch(Exception ignored){}return false;}
  try(Store store=new Store(context)){
   android.database.sqlite.SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();try{
    String fingerprint=(source.equals("SMS")?source:source+":"+sender)+":"+p.fingerprint;if(store.seen(fingerprint))return false;
    JSONArray all=store.read("entries",true);JSONObject exact=null,candidate=null;
    for(int i=0;i<all.length();i++){JSONObject old=all.getJSONObject(i);boolean sameKind=old.optString("kind").equals(p.kind),sameAmount=old.optLong("amount")==p.amount;
      if(CaptureMatcher.sameReference(p,old)){exact=old;break;}
      if(old.optBoolean("deleted"))continue;boolean sameMerchant=!p.merchantKey.isEmpty()&&p.merchantKey.equals(old.optString("merchantKey"))||p.title.equalsIgnoreCase(old.optString("title"));
      if(CaptureMatcher.possible(p,old,date,source,sender))candidate=old;
    }
    JSONObject account=store.get("accounts",p.accountId);if(account==null){account=new JSONObject().put("id",p.accountId).put("name",p.account).put("type",p.account.startsWith("Credit card")?"credit":"bank").put("opening",0).put("openingAt",0);}
    if(p.balance!=null&&date>=account.optLong("snapshotAt"))account.put("snapshot",p.balance).put("snapshotAt",date);store.put("accounts",account);
    if(!p.cardAccountId.isEmpty()&&store.get("accounts",p.cardAccountId)==null)store.put("accounts",new JSONObject().put("id",p.cardAccountId).put("name","Credit card · "+p.cardAccountId.substring(p.cardAccountId.lastIndexOf(':')+1)).put("type","credit").put("opening",0).put("openingAt",0));
    JSONObject settings=store.settings();settings.put("lastCaptureAt",System.currentTimeMillis()).put("lastCaptureSource",source);store.setSettings(settings);
    if(exact!=null){JSONArray sources=exact.optJSONArray("sources");if(sources==null)sources=new JSONArray().put(exact.optString("source"));boolean exists=false;for(int i=0;i<sources.length();i++)if(sources.optString(i).equals(source))exists=true;if(!exists)sources.put(source);exact.put("sources",sources);if(exact.optBoolean("pending")&&exact.optString("accountId").equals("bank:default")&&!p.accountId.equals("bank:default"))exact.put("accountId",p.accountId).put("account",p.account).put("bank",p.bank);if(exact.optString("kind").equals("card_payment")&&exact.optString("toAccountId").isEmpty()&&!p.cardAccountId.isEmpty())exact.put("toAccountId",p.cardAccountId);store.put("entries",exact);store.markSeen(fingerprint,exact.optString("id"));db.setTransactionSuccessful();return false;}
    String learned=store.learned(p.merchantKey);if(p.kind.equals("expense")&&!learned.isEmpty()){p.category=learned;p.pending=false;}
    if(p.cashWithdrawal){p.category="Transfer";p.pending=false;}
    JSONObject row=new JSONObject().put("id",UUID.randomUUID().toString()).put("amount",p.amount).put("kind",p.kind).put("title",p.title).put("category",p.category).put("account",p.account).put("accountId",p.accountId).put("toAccountId",p.cashWithdrawal?"cash":p.cardAccountId).put("person","").put("loanId","").put("subId","").put("cardId","").put("date",date).put("pending",p.pending||source.equals("Email")&&learned.isEmpty()||candidate!=null).put("source",source).put("sourcePackage",source.equals("SMS")?"":sender).put("sources",new JSONArray().put(source)).put("note","").put("merchantKey",p.merchantKey).put("reference",p.reference).put("bank",p.bank);
    if(candidate!=null)row.put("duplicateOf",candidate.optString("id"));
    JSONArray subscriptions=store.subscriptions();JSONObject matched=null;
    for(int i=0;i<subscriptions.length();i++){JSONObject sub=subscriptions.getJSONObject(i);if(!sub.optBoolean("active",true)||!p.kind.equals("expense")||!row.optString("duplicateOf").isEmpty())continue;String subAccount=sub.optString("accountId");if(!subAccount.isEmpty()&&!subAccount.equals("bank:default")&&!p.accountId.endsWith(":unknown")&&!subAccount.equals(p.accountId))continue;boolean same=!p.merchantKey.isEmpty()&&p.merchantKey.equals(sub.optString("merchantKey"))||p.title.equalsIgnoreCase(sub.optString("name"));if(same&&p.amount==sub.optLong("amount")&&Math.abs(java.time.LocalDate.parse(sub.optString("nextDue")).toEpochDay()-java.time.Instant.ofEpochMilli(date).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay())<=(sub.optString("cycle").equals("weekly")?2:5)){if(matched!=null){matched=null;break;}matched=sub;}}
    if(matched!=null){row.put("subId",matched.getString("id")).put("category",matched.optString("category",p.category));matched.put("nextDue",Ledger.nextDue(matched,date)).put("lastPaidAt",date);store.put("subscriptions",matched);}
    store.add(row,null);store.markSeen(fingerprint,row.getString("id"));db.setTransactionSuccessful();if(notify&&row.optBoolean("pending"))SmsReceiver.sendNotification(context,row);return true;
   }finally{db.endTransaction();context.sendBroadcast(new Intent("in.paisa.DATA_CHANGED").setPackage(context.getPackageName()));}
  }catch(Exception ignored){return false;}
 }
}
