package in.paisa.personal;

import android.content.*;
import org.json.*;
import java.util.*;

public final class Capture {
    private static void accounts(Store store,SmsParser.Parsed parsed,long date)throws Exception {
        JSONObject account=store.get("accounts",parsed.accountId);
        if(account==null)account=new JSONObject().put("id",parsed.accountId).put("name",parsed.account).put("type",parsed.account.startsWith("Credit card")?"credit":"bank").put("opening",0).put("openingAt",0);
        if(parsed.balance!=null&&date>=account.optLong("snapshotAt"))account.put("snapshot",parsed.balance).put("snapshotAt",date);
        store.put("accounts",account);
        if(!parsed.cardAccountId.isEmpty()&&store.get("accounts",parsed.cardAccountId)==null)store.put("accounts",new JSONObject().put("id",parsed.cardAccountId).put("name","Credit card · "+parsed.cardAccountId.substring(parsed.cardAccountId.lastIndexOf(':')+1)).put("type","credit").put("opening",0).put("openingAt",0));
        if(!parsed.transferTarget.isEmpty()&&store.get("accounts",parsed.transferTarget)==null)store.put("accounts",new JSONObject().put("id",parsed.transferTarget).put("name",parsed.transferTarget.equals("cash")?"Cash":"UPI Lite").put("type","cash").put("opening",0).put("openingAt",0));
    }
    private static void sources(JSONObject row,String source)throws Exception {
        JSONArray list=row.optJSONArray("sources");if(list==null)list=new JSONArray().put(row.optString("source"));
        boolean found=false;for(int i=0;i<list.length();i++)if(list.optString(i).equals(source))found=true;
        if(!found)list.put(source);row.put("sources",list);
    }
    private static void changed(Context context){context.sendBroadcast(new Intent("in.paisa.DATA_CHANGED").setPackage(context.getPackageName()));}
    public static boolean ingest(Context context,String sender,String body,long date,String source,boolean notify){return ingest(context,sender,body,date,source,notify,date);}
    public static boolean ingest(Context context,String sender,String body,long date,String source,boolean notify,long receiptDate){
        if(body==null||body.length()>200000)return false;
        if(source.equals("SMS")&&(sender==null||!sender.matches("(?i)[a-z0-9-]{3,24}")||!sender.matches(".*[a-zA-Z].*")))return false;
        if(source.equals("Email")&&!EmailText.hasFinancialContext(body))return false;
        String hint=SmsParser.bankName(sender==null?"":sender).replaceAll("[^a-zA-Z0-9]","");
        String parserSender=source.equals("SMS")?sender:hint.equals("Bank")?"BANKALERT":hint;
        String parserBody=source.equals("SMS")?body:body+" bank";
        SmsParser.Parsed parsed=SmsParser.parse(parserSender,parserBody,date);
        String scope=source.equals("SMS")?"SMS:":source+":"+sender+":";
        try(Store store=new Store(context)){
            android.database.sqlite.SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();
            try {
                JSONArray all=store.read("entries",true);
                String priorId=CapturePolicy.existingId(scope,parserSender,parserBody,date,receiptDate,parsed,store::seenEntryId);
                JSONObject prior=priorId.isEmpty()?null:store.find(priorId);
                if(parsed==null){
                    boolean revised=false;
                    if(source.equals("SMS")&&CapturePolicy.unreviewed(prior)&&prior.optInt("parserRevision")<SmsParser.REVISION){
                        store.commit("Review unconfirmed SMS",new JSONArray().put(new JSONObject().put("table","entries").put("row",CapturePolicy.flag(prior))));revised=true;
                    }
                    JSONObject bill=!source.equals("SMS")||SmsParser.supportedSender(sender)?NoticeParser.bill(body,sender,date):null;
                    if(bill!=null){
                        boolean exists=store.get("bills",bill.getString("id"))!=null;
                        JSONArray bills=store.bills();for(int i=0;i<bills.length();i++){JSONObject old=bills.getJSONObject(i);if(old.optString("accountId").equals(bill.optString("accountId"))&&old.optString("due").equals(bill.optString("due")))exists=true;}
                        if(!exists){
                            if(store.get("accounts",bill.optString("accountId"))==null)store.put("accounts",new JSONObject().put("id",bill.optString("accountId")).put("name",bill.optString("name")).put("type","credit").put("opening",0).put("openingAt",0));
                            store.put("bills",bill.put("source",source));revised=true;
                        }
                    }
                    db.setTransactionSuccessful();return revised;
                }
                String learned=store.learned(parsed.merchantKey);
                if(parsed.kind.equals("expense")&&!learned.isEmpty()){parsed.category=learned;parsed.pending=false;}
                if(prior!=null){
                    boolean revised=false;
                    if(source.equals("SMS")&&CapturePolicy.unreviewed(prior)&&prior.optInt("parserRevision")<SmsParser.REVISION){
                        accounts(store,parsed,date);JSONObject corrected=CapturePolicy.revise(prior,parsed);
                        if(CapturePolicy.changed(prior,corrected)){store.commit("Recheck captured SMS",new JSONArray().put(new JSONObject().put("table","entries").put("row",corrected)));revised=true;}
                        else store.put("entries",corrected);
                    }else if(!prior.optBoolean("deleted")&&prior.optString("reference").isEmpty()&&!parsed.reference.isEmpty()){
                        // Source metadata may be enriched without changing a user's accounting choice.
                        prior.put("reference",parsed.reference);store.put("entries",prior);
                    }
                    store.markSeen(scope+parsed.fingerprint,priorId);db.setTransactionSuccessful();return revised;
                }
                JSONObject exact=null,candidate=null;
                for(int i=0;i<all.length();i++){
                    JSONObject old=all.getJSONObject(i);
                    if(CaptureMatcher.sameReference(parsed,old)){exact=old;break;}
                    if(!old.optBoolean("deleted")&&CaptureMatcher.possible(parsed,old,date,source,sender))candidate=old;
                }
                accounts(store,parsed,date);
                JSONObject settings=store.settings();settings.put("lastCaptureAt",System.currentTimeMillis()).put("lastCaptureSource",source);store.setSettings(settings);
                if(exact!=null){
                    sources(exact,source);
                    if(exact.optBoolean("pending")&&exact.optString("accountId").equals("bank:default")&&!parsed.accountId.equals("bank:default"))exact.put("accountId",parsed.accountId).put("account",parsed.account).put("bank",parsed.bank);
                    if(exact.optString("kind").equals("card_payment")&&exact.optString("toAccountId").isEmpty()&&!parsed.cardAccountId.isEmpty())exact.put("toAccountId",parsed.cardAccountId);
                    store.put("entries",exact);store.markSeen(scope+parsed.fingerprint,exact.optString("id"));db.setTransactionSuccessful();return false;
                }
                JSONObject row=new JSONObject().put("id",UUID.randomUUID().toString()).put("amount",parsed.amount).put("kind",parsed.kind).put("title",parsed.title).put("category",parsed.category)
                    .put("account",parsed.account).put("accountId",parsed.accountId).put("toAccountId",parsed.transferTarget.isEmpty()?parsed.cardAccountId:parsed.transferTarget)
                    .put("person","").put("loanId","").put("subId","").put("cardId","").put("date",date)
                    .put("pending",parsed.pending||source.equals("Email")&&learned.isEmpty()||candidate!=null).put("source",source).put("sourcePackage",source.equals("SMS")?"":sender)
                    .put("sources",new JSONArray().put(source)).put("note","").put("merchantKey",parsed.merchantKey).put("reference",parsed.reference).put("bank",parsed.bank).put("parserRevision",SmsParser.REVISION);
                if(candidate!=null)row.put("duplicateOf",candidate.optString("id"));
                JSONArray subscriptions=store.subscriptions();JSONObject matched=null;
                for(int i=0;i<subscriptions.length();i++){
                    JSONObject sub=subscriptions.getJSONObject(i);if(!sub.optBoolean("active",true)||!parsed.kind.equals("expense")||!row.optString("duplicateOf").isEmpty())continue;
                    String account=sub.optString("accountId");if(!account.isEmpty()&&!account.equals("bank:default")&&!parsed.accountId.endsWith(":unknown")&&!account.equals(parsed.accountId))continue;
                    boolean same=!parsed.merchantKey.isEmpty()&&parsed.merchantKey.equals(sub.optString("merchantKey"))||parsed.title.equalsIgnoreCase(sub.optString("name"));
                    long distance=Math.abs(java.time.LocalDate.parse(sub.optString("nextDue")).toEpochDay()-java.time.Instant.ofEpochMilli(date).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay());
                    if(same&&parsed.amount==sub.optLong("amount")&&distance<=(sub.optString("cycle").equals("weekly")?2:5)){if(matched!=null){matched=null;break;}matched=sub;}
                }
                if(matched!=null){row.put("subId",matched.getString("id")).put("category",matched.optString("category",parsed.category));matched.put("nextDue",Ledger.nextDue(matched,date)).put("lastPaidAt",date);store.put("subscriptions",matched);}
                store.add(row,null);store.markSeen(scope+parsed.fingerprint,row.getString("id"));db.setTransactionSuccessful();
                if(notify&&row.optBoolean("pending"))SmsReceiver.sendNotification(context,row);
                return true;
            }finally{db.endTransaction();changed(context);}
        }catch(Exception ignored){return false;}
    }
}
