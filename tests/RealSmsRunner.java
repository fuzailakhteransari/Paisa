package in.paisa.personal;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Local-only corpus adapter. It writes private results to the requested local file. */
public final class RealSmsRunner {
    public static void main(String[] args) throws Exception {
        int total=0,detected=0,bills=0,errors=0;
        Map<String,Integer> kinds=new TreeMap<>();
        try (BufferedReader input=new BufferedReader(new InputStreamReader(new FileInputStream(args[0]),StandardCharsets.UTF_8));
             BufferedWriter output=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(args[1]),StandardCharsets.UTF_8))) {
            String line;
            while ((line=input.readLine())!=null) {
                JSONObject message=new JSONObject(line),result=new JSONObject().put("id",message.getInt("id"));
                total++;
                try {
                    String sender=message.optString("address"),body=message.optString("body");
                    boolean inbox=message.optInt("type")==1;
                    boolean senderAllowed=sender.matches("(?i)[a-z0-9-]{3,24}")&&sender.matches(".*[a-zA-Z].*");
                    long date=message.optLong("sentDate")>0?message.optLong("sentDate"):message.getLong("date");
                    SmsParser.Parsed parsed=inbox&&senderAllowed&&body.length()<=200000?SmsParser.parse(sender,body,date):null;
                    if (parsed!=null) {
                        JSONObject row=new JSONObject().put("amount",parsed.amount).put("kind",parsed.kind)
                            .put("title",parsed.title).put("account",parsed.account).put("accountId",parsed.accountId)
                            .put("cardAccountId",parsed.cardAccountId).put("bank",parsed.bank).put("category",parsed.category)
                            .put("reference",parsed.reference).put("fingerprint",parsed.fingerprint).put("merchantKey",parsed.merchantKey)
                            .put("cashWithdrawal",parsed.cashWithdrawal).put("transferTarget",parsed.transferTarget).put("pending",parsed.pending).put("date",date);
                        if(parsed.balance!=null)row.put("balance",parsed.balance);
                        result.put("parsed",row);detected++;kinds.put(parsed.kind,kinds.getOrDefault(parsed.kind,0)+1);
                    } else {
                        result.put("parsed",JSONObject.NULL);
                        JSONObject bill=inbox&&SmsParser.supportedSender(sender)&&body.length()<=200000?NoticeParser.bill(body,sender,date):null;
                        if(bill!=null){result.put("bill",bill);bills++;}
                    }
                    if(inbox&&senderAllowed)result.put("previousFingerprint",SmsParser.previousFingerprint(sender,body,message.getLong("date")));
                } catch(Exception failure){result.put("error",failure.getClass().getSimpleName());errors++;}
                output.write(result.toString());output.newLine();
            }
        }
        System.out.println(new JSONObject().put("total",total).put("detected",detected).put("billNotices",bills).put("errors",errors).put("kinds",kinds));
    }
}
