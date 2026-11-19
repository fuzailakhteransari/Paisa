package in.paisa.personal;

import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Runs shared domain policies on the approved private corpus; never prints its records. */
public final class RealSmsScenarios {
    private static int checks;
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    private static List<JSONObject> read(String file)throws Exception{List<JSONObject> rows=new ArrayList<>();try(BufferedReader in=Files.newBufferedReader(Paths.get(file),StandardCharsets.UTF_8)){String line;while((line=in.readLine())!=null)rows.add(new JSONObject(line));}return rows;}
    private static JSONObject copy(JSONObject row)throws Exception{return new JSONObject(row.toString());}
    private static long date(JSONObject message){return message.optLong("sentDate")>0?message.optLong("sentDate"):message.optLong("date");}
    private static boolean eligible(JSONObject message){String s=message.optString("address");return message.optInt("type")==1&&s.matches("(?i)[a-z0-9-]{3,24}")&&s.matches(".*[a-zA-Z].*");}
    private static JSONObject entry(String id,JSONObject parsed,long date)throws Exception{String target=parsed.optString("transferTarget");if(target.isEmpty())target=parsed.optBoolean("cashWithdrawal")?"cash":parsed.optString("cardAccountId");return new JSONObject(parsed.toString()).put("id",id).put("date",date).put("source","SMS").put("sourcePackage","").put("person","").put("note","").put("loanId","").put("cardId","").put("billId","").put("subId","").put("toAccountId",target).put("sources",new JSONArray().put("SMS"));}
    private static JSONObject parsed(SmsParser.Parsed p)throws Exception{return new JSONObject().put("amount",p.amount).put("kind",p.kind).put("title",p.title).put("category",p.category).put("account",p.account).put("accountId",p.accountId).put("reference",p.reference).put("bank",p.bank).put("pending",p.pending).put("merchantKey",p.merchantKey).put("cardAccountId",p.cardAccountId).put("transferTarget",p.transferTarget).put("cashWithdrawal",p.cashWithdrawal).put("balance",p.balance==null?JSONObject.NULL:p.balance);}
    private static final class Model {
        final Map<String,JSONObject> rows=new LinkedHashMap<>(),accounts=new LinkedHashMap<>(),bills=new LinkedHashMap<>();
        final Map<String,String> seen=new HashMap<>();
        int added,repaired,flagged;
        Model()throws Exception{account("cash","Cash","cash");account("bank:default","Bank","bank");}
        void account(String id,String name,String type)throws Exception{if(!accounts.containsKey(id))accounts.put(id,new JSONObject().put("id",id).put("name",name).put("type",type).put("opening",0).put("openingAt",0));}
        void accounts(JSONObject p)throws Exception{account(p.getString("accountId"),p.getString("account"),p.getString("account").startsWith("Credit card")?"credit":"bank");String card=p.optString("cardAccountId"),target=p.optString("transferTarget");if(p.has("balance")&&!p.isNull("balance")){JSONObject a=accounts.get(p.getString("accountId"));if(p.optLong("date")>=a.optLong("snapshotAt"))a.put("snapshot",p.getLong("balance")).put("snapshotAt",p.optLong("date"));}if(!card.isEmpty())account(card,"Credit card","credit");if(!target.isEmpty())account(target,target.equals("cash")?"Cash":"UPI Lite","cash");}
        void ingest(JSONObject message)throws Exception{
            if(!eligible(message))return;
            String sender=message.getString("address"),body=message.getString("body");long date=date(message);
            SmsParser.Parsed p=SmsParser.parse(sender,body,date);
            String id=CapturePolicy.existingId("SMS:",sender,body,date,message.getLong("date"),p,seen::get);
            JSONObject old=rows.get(id);
            if(p==null){
                if(CapturePolicy.unreviewed(old)&&old.optInt("parserRevision")<SmsParser.REVISION){rows.put(id,CapturePolicy.flag(old));flagged++;}
                if(SmsParser.supportedSender(sender)){JSONObject bill=NoticeParser.bill(body,sender,date);if(bill!=null){account(bill.getString("accountId"),bill.getString("name"),"credit");bills.putIfAbsent(bill.getString("id"),bill);}}
                return;
            }
            if(old!=null){
                if(CapturePolicy.unreviewed(old)&&old.optInt("parserRevision")<SmsParser.REVISION){accounts(parsed(p).put("date",date));JSONObject next=CapturePolicy.revise(old,p,date);if(CapturePolicy.changed(old,next))repaired++;rows.put(id,next);}
                else if(!old.optBoolean("deleted")&&old.optString("reference").isEmpty()&&!p.reference.isEmpty())old.put("reference",p.reference);
                seen.putIfAbsent("SMS:"+p.fingerprint,id);return;
            }
            for(JSONObject row:rows.values())if(CaptureMatcher.sameReference(p,row)){seen.putIfAbsent("SMS:"+p.fingerprint,row.getString("id"));return;}
            id="sms:"+message.getInt("id");JSONObject value=parsed(p).put("date",date);accounts(value);rows.put(id,entry(id,value,date).put("parserRevision",SmsParser.REVISION));seen.put("SMS:"+p.fingerprint,id);added++;
        }
        JSONObject state()throws Exception{return new JSONObject().put("version",3).put("entries",new JSONArray(rows.values())).put("accounts",new JSONArray(accounts.values())).put("bills",new JSONArray(bills.values())).put("subscriptions",new JSONArray()).put("rules",new JSONArray()).put("settings",new JSONObject().put("cycleStartDay",1).put("monthBudget",0).put("categoryBudgets",new JSONObject()));}
    }
    public static void main(String[] args)throws Exception{
        List<JSONObject> messages=read(args[0]),baseline=read(args[1]);Map<Integer,JSONObject> byId=new HashMap<>();for(JSONObject m:messages)byId.put(m.getInt("id"),m);
        Model fresh=new Model();List<JSONObject> sorted=new ArrayList<>(messages);sorted.sort((a,b)->Long.compare(date(b),date(a)));
        for(JSONObject m:sorted)fresh.ingest(m);
        int unique=fresh.rows.size(),billCount=fresh.bills.size();check(unique>0,"real transactions imported");check(billCount>0,"real bill notice imported");
        for(JSONObject m:sorted)fresh.ingest(m);
        check(fresh.rows.size()==unique&&fresh.added==unique,"replaying the full corpus adds no duplicate transactions");check(fresh.bills.size()==billCount,"replaying notices adds no duplicate bills");
        JSONObject state=fresh.state();Ledger.validate(state);check(true,"complete real-data ledger passes native validation");
        boolean cash=false,lite=false,card=false;
        for(JSONObject row:fresh.rows.values()){
            if(row.optBoolean("cashWithdrawal")){cash=true;check(row.optString("kind").equals("transfer")&&row.optString("toAccountId").equals("cash"),"actual withdrawal is a cash transfer");}
            if(row.optString("transferTarget").equals("wallet:upi-lite")){lite=true;check(row.optString("kind").equals("transfer")&&row.optString("toAccountId").equals("wallet:upi-lite"),"actual UPI Lite top-up is a wallet transfer");}
            if(row.optString("kind").equals("card_payment")){card=true;check(!row.optString("cardAccountId").isEmpty(),"actual card receipt has a card destination");}
        }
        check(cash&&lite&&card,"all observed transfer/card classes exercised");
        int transferPairs=0;Set<String> paired=new HashSet<>();
        JSONArray freshRows=state.getJSONArray("entries");
        for(int i=0;i<freshRows.length();i++){
            JSONObject first=freshRows.getJSONObject(i),other=TransferLink.match(freshRows,first);
            if(other==null||!paired.add(first.optString("id")))continue;paired.add(other.optString("id"));transferPairs++;
            JSONArray patches=TransferLink.patches(freshRows,first.getString("id"),other.getString("id"));JSONObject linked=copy(state);JSONArray linkedRows=linked.getJSONArray("entries");
            for(int j=0;j<patches.length();j++){JSONObject row=patches.getJSONObject(j).getJSONObject("row");for(int k=0;k<linkedRows.length();k++)if(linkedRows.getJSONObject(k).getString("id").equals(row.getString("id")))linkedRows.put(k,row);}
            Ledger.validate(linked);check(true,"actual paired transfer is a valid ledger mutation");
            JSONArray noted=new JSONArray().put(copy(first).put("note","Sandbox outgoing note")).put(copy(other).put("note","Sandbox incoming note"));JSONArray notedPatches=TransferLink.patches(noted,first.getString("id"),other.getString("id"));check(notedPatches.getJSONObject(0).getJSONObject("row").getString("note").contains("Sandbox outgoing note")&&notedPatches.getJSONObject(0).getJSONObject("row").getString("note").contains("Sandbox incoming note"),"both notes remain searchable after linking the actual pair");
            JSONObject transfer=patches.getJSONObject(0).getJSONObject("row");check(transfer.getString("kind").equals("transfer")&&transfer.getLong("creditDate")>0&&patches.getJSONObject(1).getJSONObject("row").optBoolean("deleted"),"actual transfer keeps credit timing and both source identities");
            check(TransferLink.match(linkedRows,transfer)==null,"linked transfer is no longer suggested");
            for(JSONObject originalRow:new JSONObject[]{first,other}){JSONObject message=byId.get(Integer.parseInt(originalRow.getString("id").substring(4)));SmsParser.Parsed p=SmsParser.parse(message.getString("address"),message.getString("body"),date(message));check(CaptureMatcher.sameReference(p,transfer),"both actual bank references match the confirmed transfer on rescan");}
        }
        check(transferPairs==4,"independently reviewed two-sided bank reference count");
        Model upgrade=new Model();int compatibility=0;
        for(JSONObject result:baseline){JSONObject old=result.optJSONObject("parsed");if(old==null)continue;JSONObject message=byId.get(result.getInt("id"));String fp=SmsParser.previousFingerprint(message.getString("address"),message.getString("body"),message.getLong("date"));check(fp.equals(old.getString("fingerprint")),"old fingerprint compatibility at local case "+message.getInt("id"));compatibility++;
            if(upgrade.seen.containsKey("SMS:"+fp))continue;String id="old:"+message.getInt("id");upgrade.accounts(old);upgrade.rows.put(id,entry(id,old,message.getLong("date")));upgrade.seen.put("SMS:"+fp,id);
        }
        int oldCount=upgrade.rows.size();for(JSONObject m:sorted)upgrade.ingest(m);
        int upgradedCount=upgrade.rows.size(),repaired=upgrade.repaired,flagged=upgrade.flagged;
        check(upgradedCount>=oldCount,"rescan preserves existing record identities");check(repaired>0,"real old classifications were repaired");check(flagged>0,"real unconfirmed receipts were flagged without deletion");
        for(JSONObject m:sorted)upgrade.ingest(m);
        check(upgrade.rows.size()==upgradedCount&&upgrade.repaired==repaired&&upgrade.flagged==flagged,"upgraded corpus replay is idempotent");
        Ledger.validate(upgrade.state());check(true,"upgraded ledger stays valid");
        boolean earlierRevision=false;
        for(JSONObject message:sorted){SmsParser.Parsed p=eligible(message)?SmsParser.parse(message.getString("address"),message.getString("body"),date(message)):null;if(p==null||!p.pending||date(message)==message.optLong("date"))continue;
            Model older=new Model();JSONObject row=entry("revision-one",parsed(p),message.getLong("date")).put("parserRevision",1);older.accounts(parsed(p).put("date",date(message)));older.rows.put("revision-one",row);older.seen.put("SMS:"+p.fingerprint,"revision-one");older.ingest(message);
            check(older.rows.size()==1&&older.rows.get("revision-one").getLong("date")==date(message),"actual earlier-revision receipt timestamp is corrected without another record");earlierRevision=true;break;
        }
        check(earlierRevision,"actual earlier-revision timestamp case exercised");
        JSONObject reviewed=null;JSONObject reviewedMessage=null;
        for(JSONObject result:baseline){JSONObject old=result.optJSONObject("parsed");if(old!=null&&old.optString("kind").equals("expense")){
            JSONObject message=byId.get(result.getInt("id"));SmsParser.Parsed current=SmsParser.parse(message.getString("address"),message.getString("body"),date(message));
            if(current!=null&&current.cashWithdrawal){reviewed=entry("protected",old,message.getLong("date")).put("note","Sandbox user edit").put("pending",false).put("userEdited",true);reviewedMessage=message;break;}
        }}
        check(reviewed!=null,"actual changed case exists for user-edit protection");Model protectedModel=new Model();protectedModel.accounts(reviewed);protectedModel.rows.put("protected",reviewed);protectedModel.seen.put("SMS:"+SmsParser.previousFingerprint(reviewedMessage.getString("address"),reviewedMessage.getString("body"),reviewedMessage.getLong("date")),"protected");protectedModel.ingest(reviewedMessage);
        check(protectedModel.rows.size()==1&&protectedModel.rows.get("protected").optString("note").equals("Sandbox user edit")&&protectedModel.rows.get("protected").optString("kind").equals("expense"),"rescan preserves user-reviewed purpose and notes without duplicating");
        for(JSONObject message:sorted){SmsParser.Parsed p=eligible(message)?SmsParser.parse(message.getString("address"),message.getString("body"),date(message)):null;if(p!=null&&p.kind.equals("income")&&!p.reference.isEmpty()){
            JSONObject loan=entry("purpose",parsed(p),date(message)).put("kind","borrow").put("person","Sandbox partner");check(CaptureMatcher.sameReference(p,loan),"actual credit still matches after user borrowing classification");break;
        }}
        JSONObject before=copy(state.getJSONArray("entries").getJSONObject(0)).put("note","Sandbox note").put("category","Other"),after=copy(before).put("category","Travel"),later=copy(after).put("reference","later-source-metadata");
        check(MutationGuard.unchanged(later.toString(),after.toString()),"later capture metadata does not block a category Undo");JSONObject undo=MutationGuard.restore(later,before.toString());check(undo.getString("category").equals("Other")&&undo.getString("note").equals("Sandbox note")&&undo.getString("reference").equals("later-source-metadata"),"Undo preserves notes and later source identity");check(!MutationGuard.unchanged(copy(later).put("note","later user note").toString(),after.toString()),"Undo still rejects later user changes");
        byte[] original=state.toString().getBytes(StandardCharsets.UTF_8);char[] password="local-corpus-test-only".toCharArray();byte[] encrypted=BackupCipher.encrypt(original,password);check(Arrays.equals(original,BackupCipher.decrypt(encrypted,password)),"real-data snapshot encrypted restore round trip");
        try{BackupCipher.decrypt(encrypted,"different-password".toCharArray());throw new AssertionError("wrong password accepted");}catch(IllegalArgumentException expected){checks++;}
        encrypted[encrypted.length-1]^=1;try{BackupCipher.decrypt(encrypted,password);throw new AssertionError("changed ciphertext accepted");}catch(IllegalArgumentException expected){checks++;}
        Files.write(Paths.get(args[2]),state.toString().getBytes(StandardCharsets.UTF_8));
        if(args.length>4)Files.write(Paths.get(args[4]),upgrade.state().toString().getBytes(StandardCharsets.UTF_8));
        JSONObject summary=new JSONObject().put("checks",checks).put("transferPairs",transferPairs).put("uniqueTransactions",unique).put("billNotices",billCount).put("oldFingerprintCases",compatibility).put("legacyRecords",oldCount).put("upgradeRecords",upgradedCount).put("repaired",repaired).put("flaggedForReview",flagged);
        Files.write(Paths.get(args[3]),summary.toString(2).getBytes(StandardCharsets.UTF_8));System.out.println(summary);
    }
}
