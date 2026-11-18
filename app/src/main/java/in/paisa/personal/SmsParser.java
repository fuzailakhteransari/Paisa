package in.paisa.personal;

import java.util.*;
import java.util.regex.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Conservative, offline parser. Unsupported formats are left for manual entry. */
public final class SmsParser {
    public static final class Parsed {
        public long amount;
        public String kind, title, account, category, reference, fingerprint, merchantKey, bank, accountId, cardAccountId="";
        public Long balance;
        public boolean cashWithdrawal;
        public boolean pending;
    }
    private static final String MONEY="(?:INR|Rs\\.?|₹)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)";
    private static Matcher match(String p,String s) { return Pattern.compile(p,Pattern.CASE_INSENSITIVE).matcher(s); }
    private static String group(String p,String s,String fallback) { Matcher m=match(p,s); return m.find()?m.group(1).trim():fallback; }
    public static Parsed parse(String sender,String body,long timestamp) {
        if(body==null || sender==null || !sender.matches("(?i)[a-z0-9-]{3,24}") || !sender.matches(".*[a-zA-Z].*")) return null;
        String text=body.substring(0,Math.min(body.length(),20000)).replace('\n',' ').replaceAll("\\s+"," ").trim();
        String lower=text.toLowerCase(Locale.ROOT);
        if(lower.matches(".*\\b(otp|one.time.password|verification.code|failed|declined|unsuccessful|pending|scheduled|mandate|request|will be|to be debited)\\b.*")) return null;
        boolean cardPay=lower.matches(".*(?:payment (?:of .* )?received.*(?:card)|(?:card).*payment (?:of .* )?received|paid.*(?:credit card bill)|payment towards.*card).*" );
        boolean credit=lower.matches(".*\\b(credited|received|refunded)\\b.*");
        boolean debit=lower.matches(".*\\b(debited|spent|paid|sent|purchased|withdrawn)\\b.*") || lower.contains("transaction of") && lower.contains("card");
        cardPay=cardPay||debit&&lower.matches(".*(?:towards\\s+(?:your\\s+)?(?:[a-z]+\\s+){0,2}credit\\s*card|to\\s+(?:your\\s+)?(?:(?:hdfc|icici|sbi|axis|kotak)\\s+)?credit\\s*card(?:\\s+bill)?).*" );
        if(!credit&&!debit&&!cardPay) return null;
        if(!lower.matches(".*\\b(bank|a/c|acct|account|upi|card|imps|neft|rtgs)\\b.*")) return null;
        Matcher amount=match("(?:debited|credited|spent|paid|sent|received|refunded|transaction)(?:\\s+(?:by|with|for|of|amount|is|:))*\\s*"+MONEY,text);
        if(!amount.find()) { amount=match(MONEY,text); if(!amount.find()) return null; }
        long paise;
        try { paise=new BigDecimal(amount.group(1).replace(",","")).movePointRight(2).longValueExact(); }
        catch(Exception e) { return null; }
        if(paise<=0 || paise>100_000_000_000L) return null;
        Parsed p=new Parsed(); p.amount=paise;
        p.kind=cardPay?"card_payment":credit?(lower.contains("refund")?"refund":"income"):"expense";
        boolean card=lower.contains("credit card") || lower.contains("creditcard");
        String last4=group("(?:a/c|acct|account|card)(?:\\s*(?:no\\.?|number|ending|xx|x|\\*|:|-|in|with))*\\s*[xX*]*(\\d{4})(?!\\d)",text,"");
        p.account=(card&&!cardPay?"Credit card":"Bank")+(last4.isEmpty()?"":" · "+last4);
        p.bank=bankName(sender);if(p.bank.equals("Bank"))p.bank=bankName(text);
        p.accountId=(card&&!cardPay?"credit:":"bank:")+p.bank.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","-")+":"+(last4.isEmpty()?"unknown":last4);
        if(!p.bank.equals("Bank"))p.account=(card&&!cardPay?"Credit card · ":"")+p.bank+(last4.isEmpty()?"":" · "+last4);
        if(cardPay&&credit&&!debit){p.cardAccountId="credit:"+p.bank.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","-")+":"+(last4.isEmpty()?"unknown":last4);p.accountId="bank:default";p.account="Bank";p.bank="Bank";}
        String available=group("(?:avl\\.?\\s*(?:bal(?:ance)?\\.?)?|available\\s*balance|bal(?:ance)?)\\s*[:=-]?\\s*"+MONEY,text,"");
        if(!available.isEmpty()&&!card)try{p.balance=new BigDecimal(available.replace(",","")).movePointRight(2).longValueExact();}catch(Exception ignored){}
        p.cashWithdrawal=debit&&!credit&&(lower.contains("atm")||lower.contains("cash withdrawal")||lower.contains("cash withdrawn"));
        if(p.cashWithdrawal)p.kind="transfer";
        String direction=credit?"(?:\\bfrom\\s+)":"(?:\\bat\\s+|\\bto\\s+)";
        String title=cardPay?"Card bill payment":group(direction+"([a-zA-Z0-9][a-zA-Z0-9 .@&'_-]{1,80})",text,credit?"Money received":"Payment");
        p.title=title.split("(?i)\\s+(?:on|via|using|ref|upi|avl|available|for|a/c|acct|account|bank|txn|transaction|bal)\\b")[0].trim();
        if(p.title.isEmpty()) p.title="Payment";
        if(p.cashWithdrawal)p.title="ATM withdrawal";
        p.merchantKey=merchantKey(text,p.kind,p.title);
        // Recipient account numbers are used as hashes, not retained in titles.
        if(p.title.matches("(?i)^(?:a/c|acct|account)\\b.*"))p.title="Recipient account";
        p.reference=group("(?:UPI\\s*(?:ref(?:erence)?(?:\\s*no\\.?)?|txn)|UTR|IMPS\\s*ref|NEFT\\s*ref|ref(?:erence)?(?:\\s*no\\.?)?)\\s*[:#.-]?\\s*([A-Z0-9]{8,24})",text,"");
        p.category="Unsorted";
        if(p.kind.equals("expense")) {
            if(lower.matches(".*\\b(swiggy|zomato|restaurant|cafe|dominos|mcdonalds)\\b.*")) p.category="Food";
            else if(lower.matches(".*\\b(uber|ola|rapido|irctc|metro)\\b.*")) p.category="Travel";
            else if(lower.matches(".*\\b(blinkit|zepto|bigbasket|dmart)\\b.*")) p.category="Groceries";
            else if(lower.matches(".*\\b(netflix|spotify|hotstar|youtube)\\b.*")) p.category="Subscriptions";
        } else if(p.kind.equals("card_payment")) p.category="Card bill";
        // Incoming transfers may be salary, a loan or a repayment: always ask.
        p.pending=p.category.equals("Unsorted")||cardPay;
        p.fingerprint=hash(p.reference.isEmpty()?sender+"|"+text+"|"+timestamp:p.reference+"|"+p.kind+"|"+paise+"|"+last4);
        return p;
    }
    public static String bankName(String text){String t=text.toUpperCase(Locale.ROOT);String[][] banks={{"HDFC","HDFC Bank"},{"ICICI","ICICI Bank"},{"SBI","SBI"},{"STATE BANK","SBI"},{"AXIS","Axis Bank"},{"KOTAK","Kotak Bank"},{"IDFC","IDFC First Bank"},{"INDUS","IndusInd Bank"},{"YESBANK","Yes Bank"},{"PNB","PNB"},{"CANARA","Canara Bank"},{"UNION BANK","Union Bank"},{"BANK OF BARODA","Bank of Baroda"}};int first=Integer.MAX_VALUE;String bank="Bank";for(String[] b:banks){int at=t.indexOf(b[0]);if(at>=0&&at<first){first=at;bank=b[1];}}return bank;}
    public static String nameKey(String title) {
        if(title==null)return "";
        String name=title.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+"," ");
        if(name.length()<2||name.matches("(?:payment|upi(?: payment)?|money received|card bill payment|recipient account|bank|account|your.*|a/c.*|acct.*|account .*)"))return "";
        return "name:"+name;
    }
    private static String merchantKey(String text,String kind,String title) {
        if(!kind.equals("expense"))return "";
        String upi=group("(?:\\bto|\\bat|\\bpayee\\s+vpa|\\bbeneficiary\\s+vpa)\\s*[:=-]?\\s*([a-zA-Z0-9][a-zA-Z0-9._-]{1,80}@[a-zA-Z][a-zA-Z0-9.-]{1,40})",text,"");
        if(!upi.isEmpty())return "upi:"+hash(upi.toLowerCase(Locale.ROOT).replaceAll("[.,;:]+$",""));
        String recipient=group("(?:\\bto|\\bbeneficiary)\\s+(?:a/c|acct|account)(?:\\s*(?:no\\.?|number))?\\s*[:.-]?\\s*(\\d{8,20})(?!\\d)",text,"");
        if(!recipient.isEmpty())return "acct:"+hash(recipient);
        return nameKey(title);
    }
    private static String hash(String s) {
        try { byte[] bytes=MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)); StringBuilder b=new StringBuilder(); for(byte x:bytes)b.append(String.format("%02x",x));return b.toString(); }
        catch(Exception e){throw new IllegalStateException(e);}
    }
}
