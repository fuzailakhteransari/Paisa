package in.paisa.personal;
import org.json.*;
import java.util.*;
import java.util.regex.*;
import java.time.*;
import java.time.format.*;
import java.math.BigDecimal;

public final class NoticeParser {
 public static JSONObject bill(String text,String sender,long timestamp)throws Exception{
  String t=text.replaceAll("\\s+"," ");if(!t.toLowerCase(Locale.ROOT).contains("due")||!t.toLowerCase(Locale.ROOT).contains("card"))return null;
  Matcher amount=Pattern.compile("(?:total\\s+(?:amount\\s+)?due|bill\\s+(?:amount|of))\\s*(?:is|:|-)?\\s*(?:INR|Rs\\.?|₹)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)",Pattern.CASE_INSENSITIVE).matcher(t);if(!amount.find())return null;long n=new BigDecimal(amount.group(1).replace(",","")).movePointRight(2).longValueExact();if(n<=0||n>Ledger.MAX)return null;
  Matcher date=Pattern.compile("(?:due\\s*(?:date|on|by)?\\s*[:=-]?\\s*)(\\d{4}-\\d{2}-\\d{2}|\\d{1,2}[-/ ](?:[A-Za-z]{3}|\\d{1,2})[-/ ]\\d{2,4})",Pattern.CASE_INSENSITIVE).matcher(t);if(!date.find())return null;LocalDate due=null;for(String f:new String[]{"uuuu-MM-dd","d-MMM-uuuu","d/MM/uuuu","d-MM-uuuu","d MMM uuuu","d/MM/uu"})try{due=LocalDate.parse(date.group(1),new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(f).toFormatter(Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT));break;}catch(Exception ignored){}if(due==null)return null;
  String bank=SmsParser.bankName(sender+" "+t);Matcher card=Pattern.compile("card(?:\\s*(?:ending|xx|x|no\\.?|number|:|in))*\\s*[xX*]*(\\d{4})(?!\\d)",Pattern.CASE_INSENSITIVE).matcher(t);String last=card.find()?card.group(1):"unknown";String account="credit:"+bank.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","-")+":"+last;
  return new JSONObject().put("id","bill:"+account+":"+due).put("name",bank+" · "+last).put("accountId",account).put("amount",n).put("due",due.toString()).put("kind","card").put("createdAt",timestamp);
 }
}
