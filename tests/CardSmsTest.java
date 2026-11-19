package in.paisa.personal;

import org.json.JSONObject;

/** Fictional identities and references, matching the reported HDFC message layout. */
public final class CardSmsTest {
    private static int checks;
    private static final long WHEN = 1795028400000L;

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        checks++;
    }

    private static String message(String amount, String payee, String reference) {
        return "Txn Rs." + amount + "\nOn HDFC Bank Card 4242\nAt " + payee
                + "\nby UPI " + reference + "\nOn 18-11\nNot You?\n"
                + "Call 1800000000/SMS BLOCK CC 4242 to 7300000000";
    }

    private static SmsParser.Parsed parse(String text) {
        return SmsParser.parse("JX-HDFCBK-S", text, WHEN);
    }

    public static void main(String[] args) throws Exception {
        String purchaseA = message("125.00", "*2468@ybl", "112233445566");
        String purchaseB = message("275.00", "*1357@hdfcb", "556677889900");
        SmsParser.Parsed a = parse(purchaseA), b = parse(purchaseB);
        check(a != null, "HDFC Txn Rs.125 card alert is detected");
        check(b != null, "HDFC Txn Rs.275 card alert is detected");
        check(a.amount == 12500 && b.amount == 27500, "amounts are integer paise");
        check(a.amount + b.amount == 40000, "two card purchases total Rs.400");
        check(a.kind.equals("expense") && b.kind.equals("expense"), "purchases are expenses, not card bill payments");
        check(a.account.startsWith("Credit card"), "BLOCK CC identifies a credit card");
        check(a.accountId.equals("credit:hdfc-bank:4242") && b.accountId.equals(a.accountId), "purchases use the same credit card account");
        check(a.reference.equals("112233445566") && b.reference.equals("556677889900"), "bare by UPI references are extracted");
        check(a.pending && a.category.equals("Unsorted"), "unknown purpose is queued for review");
        check(a.title.contains("*2468@ybl") && !a.title.contains("Not You"), "masked payee is shown without the SMS footer");
        check(a.merchantKey.isEmpty() && b.merchantKey.isEmpty(), "masked VPAs are not treated as unique remembered merchants");

        SmsParser.Parsed repeated = SmsParser.parse("AX-HDFCBK-S", purchaseA.replace('\n', ' '), WHEN + 90000);
        check(repeated != null && repeated.fingerprint.equals(a.fingerprint), "re-import and sender-prefix changes preserve duplicate identity");
        JSONObject stored = new JSONObject().put("reference", a.reference).put("amount", a.amount)
                .put("kind", a.kind).put("bank", a.bank).put("accountId", a.accountId);
        check(CaptureMatcher.sameReference(repeated, stored), "duplicate reference matches an existing captured expense");
        SmsParser.Parsed another = parse(message("125.00", "*2468@ybl", "112233445567"));
        check(!another.fingerprint.equals(a.fingerprint) && !CaptureMatcher.sameReference(another, stored), "separate same-amount purchases remain separate");

        SmsParser.Parsed fullPayee = parse(message("275.00", "fictional-shop@upi", "112233445568"));
        check(fullPayee.merchantKey.startsWith("upi:"), "a complete unmasked VPA can have a hashed merchant identity");
        check(!fullPayee.merchantKey.contains("fictional-shop"), "merchant keys do not store the VPA in plain text");
        SmsParser.Parsed debit = parse(message("275.00", "*1357@hdfcb", "112233445569")
                .replace("Bank Card", "Bank Debit Card").replace("BLOCK CC", "BLOCK DC"));
        check(debit != null && debit.accountId.startsWith("bank:"), "debit-card variant is not made a credit-card expense");
        SmsParser.Parsed explicitCredit = parse(message("275.00", "*1357@hdfcb", "112233445570")
                .replace("Bank Card", "Bank Credit Card").split("Not You")[0]);
        check(explicitCredit != null && explicitCredit.accountId.startsWith("credit:"), "explicit credit-card alert does not need a fraud footer");

        for (String suffix : new String[]{" Transaction failed", " Transaction declined", " Transaction pending", " OTP 123456"}) {
            check(parse(purchaseA + suffix) == null, "unsuccessful or authorization-only alert stays ignored: " + suffix);
        }
        check(parse("Txn Rs.125.00 On HDFC Bank Card 4242. Available limit Rs.27500") == null,
                "a vague card/amount mention is not proof of a completed purchase");
        SmsParser.Parsed receipt = parse("Payment of INR 500 received towards HDFC credit card XX4242. UPI Ref 112233445571");
        check(receipt != null && receipt.kind.equals("card_payment"), "card bill receipts keep their existing classification");
        System.out.println(checks + " HDFC card SMS regression checks passed");
    }
}
