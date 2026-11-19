package in.paisa.personal;

import java.util.*;
import java.util.regex.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Conservative, offline parser. Unsupported formats are left for manual entry. */
public final class SmsParser {
    public static final int REVISION=1;
    public static final class Parsed {
        public long amount;
        public String kind, title, account, category, reference, fingerprint, merchantKey, bank, accountId, cardAccountId="";
        public Long balance;
        public String transferTarget="", previousFingerprint="";
        public boolean cashWithdrawal;
        public boolean pending;
    }
    private static final String MONEY="(?:INR\\s*\\.?|Rs\\s*\\.?|₹)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)";
    private static Matcher match(String p,String s) { return Pattern.compile(p,Pattern.CASE_INSENSITIVE).matcher(s); }
    private static String group(String p,String s,String fallback) { Matcher m=match(p,s); return m.find()?m.group(1).trim():fallback; }

    private static final Set<String> BANK_SENDERS=new HashSet<>(Arrays.asList(
        "HDFCBK","HDFCBN","HDFCBANK","HDFC","PNBSMS","PNBBNK","PNB","SBIBNK","SBIINB","SBIUPI","SBIYNO","SBI",
        "ICICIB","ICICIS","ICICIBANK","ICICI","AXISBK","AXISBANK","AXIS","KOTAKB","KOTAKBANK","KOTAK","IDFCFB","IDFCBANK","IDFC",
        "INDBNK","INDUSINDBK","INDUSINDBANK","YESBNK","YESBANK","CANBNK","CANBANK","CANARA","UNIONB","UNIONBANK","BOBTXN","BOBSMS","BOB",
        "BANKALERT"
    ));
    public static boolean supportedSender(String sender){
        if(sender==null||!sender.matches("(?i)[a-z0-9-]{3,24}")||!sender.matches(".*[a-zA-Z].*"))return false;
        return BANK_SENDERS.contains(sender.toUpperCase(Locale.ROOT).replaceFirst("^[A-Z]{2}-","").replaceFirst("-[STPG]$",""));
    }
    private static Matcher cardHeader(String text){return match("^txn\\s*[:.-]?\\s*"+MONEY
        +"\\s+on\\s+hdfc\\s+bank\\s+(?:(?:credit|debit)\\s+)?card\\s*(?:[x*]+\\s*)?(\\d{4})(?!\\d)"
        +"\\s+at\\s+(\\S{1,120})\\s+by\\s+upi\\s+(\\d{10,16})(?!\\d)"
        +"\\s+on\\s+\\d{2}-\\d{2}(?:-\\d{2,4})?\\b",text);}
    private static long transactionAmount(String text){
        Matcher m=match("(?:debited|credited|spent|paid|sent|received|refunded|deposited|deducted|transaction|payment)(?:\\s+(?:by|with|for|of|amount|is|:))*\\s*"+MONEY,text);
        if(!m.find()){m=match(MONEY,text);if(!m.find())return 0;}
        try{return new BigDecimal(m.group(1).replace(",","")).movePointRight(2).longValueExact();}catch(Exception ignored){return 0;}
    }
    private static String last4(String text){
        String value=group("(?:a/c|\\bac\\b|acct|account|card|\\bDC\\b|\\bCC\\b)(?:\\s*(?:no\\.?|number|ending|in|with|[:.\\-]))*\\s*[xX*]*(\\d{4,20})(?!\\d)",text,"");
        return value.length()>4?value.substring(value.length()-4):value;
    }
    private static String reference(String text){
        String ref=group("(?:UPI\\s+(?:ref(?:erence)?(?:\\s*(?:no\\.?|id|number))?|txn(?:\\s*id)?)|UTR|IMPS\\s*ref|NEFT\\s*ref|ref(?:erence)?(?:\\s*(?:no\\.?|id|number))?)\\s*[:#.-]?\\s*([A-Z0-9]{8,32})",text,"");
        if(ref.isEmpty())ref=group("\\bUPI\\s*[:#.-]?\\s*(\\d{10,16})(?!\\d)",text,"");
        return ref;
    }
    /** The old parser is only an identity adapter; none of its classifications are trusted. */
    public static String previousFingerprint(String sender,String body,long timestamp){
        PreviousSmsParser.Parsed old=PreviousSmsParser.parse(sender,body,timestamp);
        return old==null?"":old.fingerprint;
    }
    public static Parsed parse(String sender,String body,long timestamp){
        if(body==null||!supportedSender(sender))return null;
        String text=body.substring(0,Math.min(body.length(),20000)).replaceAll("\\s+"," ").trim(),lower=text.toLowerCase(Locale.ROOT);
        if(match("\\b(?:otp|one.time.password|verification.code|failed|declined|unsuccessful|pending|scheduled|will be|to be debited|can be credited|may be credited|would be credited)\\b",lower).find())return null;
        if(match("loyalty|bonus\\s*points|top.up balance|we have received.*loan|payment request|collect request|refund initiated",lower).find())return null;
        boolean mandateDebit=match("^upi\\s+mandate:\\s*sent\\s*"+MONEY+".*\\bfrom\\s+hdfc\\s+bank\\s+a/c\\b.*\\bref\\s+\\d{10,16}",text).find();
        boolean autoPay=match("^autopay\\s*\\(e-mandate\\)\\s*success!.*\\btxn\\s*amt\\s*:\\s*"+MONEY+".*\\bvia\\s*:\\s*hdfc\\s+bank\\s+(?:dc|cc)\\s+\\d{4}",text).find();
        if(lower.contains("mandate")&&!mandateDebit&&!autoPay)return null;
        Matcher hdfc=cardHeader(text);boolean header=hdfc.find();String headerLast=header?hdfc.group(2):"";
        boolean headerCredit=header&&(lower.contains("credit card")||match("\\bblock\\s+cc\\s+"+headerLast+"(?!\\d)",text).find());
        boolean headerDebit=header&&(lower.contains("debit card")||match("\\bblock\\s+dc\\s+"+headerLast+"(?!\\d)",text).find());
        boolean cardSpend=header&&(headerCredit||headerDebit);
        boolean lite=match("^upi\\s+lite\\s+top-up\\s+amounting\\s+to\\s*"+MONEY+".*has been successful",text).find();
        String eventText=text.replaceAll("[a-zA-Z0-9.*_+\\-]+@[a-zA-Z0-9.\\-]+","<vpa>");
        Matcher verb=match("\\b(credited|received|refunded|deposited|reversed|debited|deducted|spent|paid|sent|purchased|withdrawn)\\b",eventText);
        String event=verb.find()?verb.group(1).toLowerCase(Locale.ROOT):"";
        boolean credit=Arrays.asList("credited","received","refunded","deposited","reversed").contains(event);
        boolean debit=Arrays.asList("debited","deducted","spent","paid","sent","purchased","withdrawn").contains(event)||cardSpend||mandateDebit||autoPay||lite||lower.startsWith("money transfer:");
        boolean cardPay=match("payment (?:of .* )?received.*(?:card)|(?:card).*payment (?:of .* )?received|paid.*credit card bill|payment towards.*card",lower).find()
            ||match("(?:hdfc bank cardmember|credit card).*payment of.*was credited to (?:your )?card",lower).find();
        cardPay=cardPay||debit&&match("towards\\s+(?:your\\s+)?(?:[a-z]+\\s+){0,2}credit\\s*card|to\\s+(?:your\\s+)?(?:(?:hdfc|icici|sbi|axis|kotak)\\s+)?credit\\s*card(?:\\s+bill)?",lower).find();
        if(!credit&&!debit&&!cardPay)return null;
        if(!match("\\b(bank|a/c|ac|acct|account|upi|card|imps|neft|rtgs)\\b",lower).find())return null;
        long n=transactionAmount(text);if(n<=0||n>100_000_000_000L)return null;
        Parsed p=new Parsed();p.amount=n;
        boolean refund=event.equals("refunded")||event.equals("reversed");
        p.kind=cardPay?"card_payment":credit?(refund?"refund":"income"):"expense";
        boolean card=lower.contains("credit card")||lower.contains("creditcard")||headerCredit||autoPay&&match("\\bvia\\s*:\\s*hdfc\\s+bank\\s+cc\\b",lower).find();
        String suffix=cardSpend?headerLast:last4(text);
        p.bank=bankName(sender);if(p.bank.equals("Bank"))p.bank=bankName(text);
        String bankKey=p.bank.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","-");
        boolean debitCard=!card&&(autoPay||match("\\b(?:debit card|block dc)\\b",lower).find());
        p.accountId=(card&&!cardPay?"credit:":"bank:")+bankKey+":"+(suffix.isEmpty()?"unknown":suffix);
        p.account=(card&&!cardPay?"Credit card · ":"")+p.bank+(debitCard?" debit card":"")+(suffix.isEmpty()?"":" · "+suffix);
        if(cardPay&&credit&&!debit){p.cardAccountId="credit:"+bankKey+":"+(suffix.isEmpty()?"unknown":suffix);p.accountId="bank:default";p.account="Bank";p.bank="Bank";}
        String available=group("(?:av[al]*\\.?\\s*bal(?:ance)?\\.?|available\\s*balance|\\bbal(?:ance)?)\\s*[:=-]?\\s*(?:(?:INR\\.?|Rs\\.?)\\s*)?([0-9][0-9,]*(?:\\.[0-9]{1,2})?)",text,"");
        if(!available.isEmpty()&&!card&&!cardPay)try{p.balance=new BigDecimal(available.replace(",","")).movePointRight(2).longValueExact();}catch(Exception ignored){}
        p.cashWithdrawal=debit&&!credit&&event.equals("withdrawn");
        if(p.cashWithdrawal){p.kind="transfer";p.transferTarget="cash";}
        if(lite){p.kind="transfer";p.transferTarget="wallet:upi-lite";}
        String payee=cardSpend?hdfc.group(3):"";
        String title=cardSpend?(payee.contains("*")?"UPI payment · "+payee:payee):cardPay?"Card bill payment":autoPay?group("^autopay.*?success!\\s*for\\s+(.+?)\\s+txn\\s+amt",text,"AutoPay payment")
            :group(credit?"\\bfrom\\s+([a-zA-Z0-9][a-zA-Z0-9 .@&'_-]{1,80})":"(?:\\bat\\s+|\\bto\\s+)([a-zA-Z0-9][a-zA-Z0-9 .@&'_-]{1,80})",text,credit?"Money received":"Payment");
        p.title=title.split("(?i)\\s+(?:on|via|using|ref|upi|avl|available|for|a/c|acct|account|bank|txn|transaction|bal)\\b")[0].trim();
        if(p.title.isEmpty())p.title="Payment";
        if(p.cashWithdrawal)p.title="ATM withdrawal";
        if(lite)p.title="UPI Lite top-up";
        p.merchantKey=cardSpend&&payee.contains("*")?"":merchantKey(text,p.kind,p.title);
        if(p.title.matches("(?i)^(?:a/c|ac|acct|account)\\b.*"))p.title="Recipient account";
        p.reference=cardSpend?hdfc.group(4):reference(text);
        p.category="Unsorted";
        if(p.kind.equals("expense")){
            if(match("\\b(swiggy|zomato|restaurant|cafe|dominos|mcdonalds)\\b",lower).find())p.category="Food";
            else if(match("\\b(uber|ola|rapido|irctc|metro)\\b",lower).find())p.category="Travel";
            else if(match("\\b(blinkit|zepto|bigbasket|dmart)\\b",lower).find())p.category="Groceries";
            else if(match("\\b(netflix|spotify|hotstar|youtube)\\b",lower).find())p.category="Subscriptions";
        }else if(p.kind.equals("card_payment"))p.category="Card bill";
        else if(p.kind.equals("transfer"))p.category="Transfer";
        p.pending=p.category.equals("Unsorted")||cardPay;
        p.fingerprint=hash(p.reference.isEmpty()?sender+"|"+text+"|"+timestamp:p.reference+"|"+p.kind+"|"+n+"|"+suffix);
        p.previousFingerprint=previousFingerprint(sender,body,timestamp);
        return p;
    }
    public static String bankName(String text){String t=text.toUpperCase(Locale.ROOT);String[][] banks={{"HDFC","HDFC Bank"},{"ICICI","ICICI Bank"},{"SBI","SBI"},{"STATE BANK","SBI"},{"AXIS","Axis Bank"},{"KOTAK","Kotak Bank"},{"IDFC","IDFC First Bank"},{"INDUS","IndusInd Bank"},{"YESBANK","Yes Bank"},{"PNB","PNB"},{"CANARA","Canara Bank"},{"UNION BANK","Union Bank"},{"BANK OF BARODA","Bank of Baroda"}};int first=Integer.MAX_VALUE;String bank="Bank";for(String[] b:banks){int at=t.indexOf(b[0]);if(at>=0&&at<first){first=at;bank=b[1];}}return bank;}
    public static String nameKey(String title) {
        if(title==null)return "";
        String name=title.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+"," ");
        if(name.length()<2||name.matches("(?:payment|upi(?: payment)?|money received|card bill payment|recipient account|bank|account|vpa|your.*|a/c.*|acct.*|account .*)"))return "";
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
