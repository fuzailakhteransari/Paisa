package in.paisa.personal;
public class ParserTest {
  static int checks=0;
  static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
  static SmsParser.Parsed parse(String s){return SmsParser.parse("VM-HDFCBK",s,1795000000000L);}
  public static void main(String[] args){
    SmsParser.Parsed p=parse("Rs.249.00 debited from A/c XX1234 to ZOMATO on 18-Nov. UPI Ref 123456789012. Avl Bal Rs.5,000.00");
    check(p!=null,"bank debit parsed");check(p.amount==24900,"transaction amount not balance");check(p.kind.equals("expense"),"expense");check(p.category.equals("Food"),"merchant category");check(!p.pending,"known merchant");
    p=parse("INR 45,000.50 credited to your account XX1234 from ACME on 18-Nov via NEFT Ref ABCD123456789");
    check(p.amount==4500050,"credit paise");check(p.kind.equals("income")&&p.pending,"incoming requires purpose");
    p=parse("Rs 1,299 spent on HDFC Bank Credit Card XX4281 at AMAZON on 18-Nov");
    check(p.amount==129900&&p.account.startsWith("Credit card"),"card purchase");
    p=parse("Payment of INR 1500 received towards your HDFC credit card XX4281. Thank you.");
    check(p!=null&&p.kind.equals("card_payment"),"card payment not income");
    for(String x:new String[]{"Your OTP is 123456 for payment of Rs 500 using bank account", "Payment of Rs 100 failed for bank account XX1234", "INR 999 will be debited from bank account tomorrow", "Credit card bill Rs 3000 due on 25-Nov", "INR 500 request pending on UPI", "Your available bank balance is Rs 10000", "Your bank account was debited by Rs 0.00"})check(parse(x)==null,"ignore non transaction: "+x);
    check(SmsParser.parse("+910000000000","Rs 500 received in bank account",1795000000000L)==null,"ignore personal sender");
    String sms="Rs 500 debited from bank account XX1234 to ALICE UPI Ref 999999999999";
    p=parse(sms);check(p.pending,"unknown debit needs review");
    SmsParser.Parsed again=SmsParser.parse("VM-HDFCBK",sms,1795000000200L);check(p.fingerprint.equals(again.fingerprint),"same reference dedupes");
    check(parse("Rs 800 debited from bank A/c XX1234 UPI Ref 999999999998").amount==80000,"debited by amount pattern");
    p=parse("INR 249 refunded to your bank account XX1234 from ZOMATO on 18-Nov");check(p!=null&&p.kind.equals("refund")&&p.pending,"refund is not income; review category");
    check(parse("Rs 249 debited from A/c XX1234 to ZOMATO on 18-Nov").title.equals("ZOMATO"),"merchant is recipient, not own bank account");
    SmsParser.Parsed first=parse("Rs 500 debited from A/c XX1234 to alice@upi UPI Ref 123456780001");
    SmsParser.Parsed second=parse("Rs 800 debited from A/c XX5678 to ALICE@UPI. UPI Ref 123456780002");
    check(first.merchantKey.startsWith("upi:"),"UPI identity available");
    check(first.merchantKey.equals(second.merchantKey),"same payee, different own account, stable UPI identity");
    check(!first.merchantKey.equals(parse("Rs 500 debited from A/c XX1234 to bob@upi UPI Ref 123456780003").merchantKey),"different recipient does not share rule");
    check(parse("Rs 500 debited from A/c XX1234 via UPI Ref 123456780004").merchantKey.isEmpty(),"unknown recipient not own-account matched");
    check(parse("Rs 500 debited from A/c XX1234 to account XXXX5678 via NEFT").merchantKey.isEmpty(),"masked recipient account not a unique identity");
    p=parse("Rs 500 debited from A/c XX1234 to account 123456789012 via NEFT");
    check(p.merchantKey.startsWith("acct:")&&!p.merchantKey.contains("123456789012"),"recipient account identity hashed");
    check(!p.title.contains("123456789012"),"recipient account number not retained in title");
    check(p.merchantKey.equals(parse("Rs 1000 debited from A/c XX5678 to account 123456789012 via NEFT").merchantKey),"recipient account identity stable across funding account");
    check(SmsParser.nameKey("  ZOMATO   CAFE ").equals(SmsParser.nameKey("zomato cafe")),"merchant name case and spacing normalized");
    check(SmsParser.nameKey("UPI payment").isEmpty()&&SmsParser.nameKey("A/c XX1234").isEmpty(),"generic labels cannot be remembered");
    check(parse("Rs 500 credited to bank account XX1234 from alice@upi").merchantKey.isEmpty(),"incoming purposes not inferred from payee rule");
    System.out.println(checks+" SMS parsing checks passed");
  }
}
