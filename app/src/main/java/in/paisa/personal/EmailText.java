package in.paisa.personal;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
public final class EmailText {
 public static boolean hasFinancialContext(String text){return text!=null&&text.toLowerCase(Locale.ROOT).matches("(?s).*\\b(bank|a/c|acct|account|upi|credit card|debit card|hdfc|icici|sbi|axis|kotak|neft|imps)\\b.*");}
 public static String decode(String input){
  if(input==null)return "";String text=input.substring(0,Math.min(input.length(),200000));
  // Imported email is text only: never execute HTML, scripts, remote images or attachments.
  String lower=text.toLowerCase(Locale.ROOT);if(lower.contains("content-transfer-encoding: base64")){int p=lower.indexOf("content-transfer-encoding: base64"),start=text.indexOf("\r\n\r\n",p),skip=4;if(start<0){start=text.indexOf("\n\n",p);skip=2;}if(start>=0){String data=text.substring(start+skip).split("\\r?\\n--",2)[0];try{text=new String(Base64.getMimeDecoder().decode(data),StandardCharsets.UTF_8);}catch(Exception ignored){}}}
  else if(lower.contains("quoted-printable")){text=text.replace("=\r\n","").replace("=\n","");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] bytes=text.getBytes(StandardCharsets.UTF_8);for(int i=0;i<bytes.length;i++){if(bytes[i]=='='&&i+2<bytes.length){try{out.write(Integer.parseInt(new String(bytes,i+1,2,StandardCharsets.US_ASCII),16));i+=2;continue;}catch(Exception ignored){}}out.write(bytes[i]);}text=new String(out.toByteArray(),StandardCharsets.UTF_8);}
  return text.replaceAll("(?is)<(?:script|style)[^>]*>.*?</(?:script|style)>"," ").replaceAll("(?s)<[^>]+>"," ").replace("&nbsp;"," ").replace("&amp;","&").replace("&#8377;","₹").replace("&lt;","<").replace("&gt;",">");
 }
}
