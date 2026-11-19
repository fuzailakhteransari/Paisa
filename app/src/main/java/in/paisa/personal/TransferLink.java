package in.paisa.personal;

import org.json.*;

/** Suggest only exact two-sided bank references. Ownership still requires user confirmation. */
public final class TransferLink {
    private static boolean eligible(JSONObject row){
        if(row==null||!Ledger.active(row)||!row.optString("accountId").startsWith("bank:")||row.optString("accountId").endsWith(":unknown")||row.optString("accountId").equals("bank:default")||!row.optString("reference").matches("[A-Za-z0-9]{6,80}"))return false;
        for(String key:new String[]{"loanId","subId","cardId","billId"})if(!row.optString(key).isEmpty())return false;
        return row.optString("kind").equals("income")||row.optString("kind").equals("expense");
    }
    public static boolean pair(JSONObject first,JSONObject second){
        return eligible(first)&&eligible(second)&&!first.optString("id").equals(second.optString("id"))&&!first.optString("accountId").equals(second.optString("accountId"))&&!first.optString("kind").equals(second.optString("kind"))&&first.optLong("amount")==second.optLong("amount")&&first.optString("reference").equalsIgnoreCase(second.optString("reference"))&&Math.abs(first.optLong("date")-second.optLong("date"))<=86400000L;
    }
    public static JSONObject match(JSONArray rows,JSONObject row){
        JSONObject found=null;
        for(int i=0;i<rows.length();i++){JSONObject other=rows.optJSONObject(i);if(pair(row,other)){if(found!=null)return null;found=other;}}
        if(found==null)return null;
        int reverse=0;for(int i=0;i<rows.length();i++)if(pair(found,rows.optJSONObject(i)))reverse++;
        return reverse==1?found:null;
    }
    public static JSONArray patches(JSONArray rows,String firstId,String secondId)throws Exception{
        JSONObject first=Ledger.find(rows,firstId),second=Ledger.find(rows,secondId),found=match(rows,first);
        if(found==null||!found.optString("id").equals(secondId))throw new IllegalArgumentException("Matching bank entries changed. Review them again.");
        JSONObject debit=new JSONObject((first.optString("kind").equals("expense")?first:second).toString()),credit=new JSONObject((first.optString("kind").equals("income")?first:second).toString());
        String note=debit.optString("note"),incomingNote=credit.optString("note");
        if(!incomingNote.isEmpty()&&!note.contains(incomingNote)){note+=(note.isEmpty()?"":"\n")+incomingNote;if(note.length()>1000)throw new IllegalArgumentException("Combined notes exceed 1000 characters. Shorten a note before linking.");debit.put("note",note);}
        debit.put("kind","transfer").put("category","Transfers / Loans").put("toAccountId",credit.optString("accountId")).put("creditDate",credit.optLong("date")).put("transferPairId",credit.optString("id")).put("pending",false).put("userEdited",true);
        credit.put("deleted",true).put("transferLinkedTo",debit.optString("id")).put("userEdited",true);
        return new JSONArray().put(new JSONObject().put("table","entries").put("row",debit)).put(new JSONObject().put("table","entries").put("row",credit));
    }
}
