package in.paisa.personal;

import org.json.*;

/** Shared, testable rules for non-destructive rescanning of older captured SMS. */
public final class CapturePolicy {
    public interface FingerprintLookup { String find(String fingerprint); }
    public static boolean unreviewed(JSONObject row){
        if(row==null||row.optBoolean("deleted")||row.optBoolean("userEdited")||!row.optBoolean("pending")||!row.optString("source").equals("SMS")||!row.optString("note").isEmpty()||!row.optString("duplicateOf").isEmpty())return false;
        for(String field:new String[]{"loanId","subId","cardId","billId"})if(!row.optString(field).isEmpty())return false;
        return true;
    }
    public static String existingId(String scope,String sender,String body,long date,long receiptDate,SmsParser.Parsed parsed,FingerprintLookup lookup){
        if(parsed!=null){String id=lookup.find(scope+parsed.fingerprint);if(id!=null)return id;}
        String previous=parsed==null?SmsParser.previousFingerprint(sender,body,date):parsed.previousFingerprint;
        if(!previous.isEmpty()){String id=lookup.find(scope+previous);if(id!=null)return id;}
        // Older inbox imports used receipt time; broadcasts use the SMS sender time.
        // Both supplied timestamps are exact evidence. Never guess using nearby rows.
        if(scope.equals("SMS:")&&receiptDate!=date){String fingerprint=SmsParser.previousFingerprint(sender,body,receiptDate);if(!fingerprint.isEmpty()){String id=lookup.find(scope+fingerprint);if(id!=null)return id;}}
        return "";
    }
    public static JSONObject revise(JSONObject old,SmsParser.Parsed parsed)throws JSONException{
        JSONObject row=new JSONObject(old.toString());
        row.put("amount",parsed.amount).put("kind",parsed.kind).put("title",parsed.title).put("category",parsed.category)
            .put("account",parsed.account).put("accountId",parsed.accountId).put("bank",parsed.bank)
            .put("toAccountId",parsed.transferTarget.isEmpty()?parsed.cardAccountId:parsed.transferTarget)
            .put("reference",parsed.reference).put("merchantKey",parsed.merchantKey).put("pending",parsed.pending)
            .put("parserRevision",SmsParser.REVISION);
        row.remove("captureReview");
        return row;
    }
    public static JSONObject flag(JSONObject old)throws JSONException{
        return new JSONObject(old.toString()).put("captureReview","unsupported_sms").put("pending",true).put("parserRevision",SmsParser.REVISION);
    }
    public static boolean changed(JSONObject before,JSONObject after){
        for(String key:new String[]{"amount","kind","title","category","account","accountId","bank","toAccountId","reference","merchantKey","pending","captureReview"})
            if(!before.optString(key).equals(after.optString(key)))return true;
        return false;
    }
}
